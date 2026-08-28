/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.hdl.internal.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;

/**
 * Tests for {@link MW02}'s elapsed-time curtain position estimate ({@code getEstimatedPositionShutter1/2}) -
 * uses short (1s) configured durations and real sleeps rather than an injected clock, since this binding has
 * no time-abstraction to mock; kept deliberately short to stay fast. Drives state exclusively through real
 * {@link HdlPacket}s on the public {@link MW02#treatHDLPacketForDevice} entry point, matching real wire
 * traffic shape rather than reaching into private state.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
class MW02Test {

    private static final int CHANNEL_1 = 1;
    private static final int DURATION_SECONDS = 1;

    private static MW02 newDevice() {
        DeviceConfiguration config = DeviceConfiguration.create("1038", 1, 38, DeviceType.MW02);
        return new MW02(config);
    }

    private static void sendDuration(MW02 device, int channel, int durationSeconds) {
        HdlPacket p = new HdlPacket();
        p.commandType = CommandType.Get_Curtain_Duration_Response;
        p.data = new byte[] { (byte) channel, (byte) ((durationSeconds >> 8) & 0xff), (byte) (durationSeconds & 0xff) };
        device.treatHDLPacketForDevice(p);
    }

    private static void sendCurtainStatus(MW02 device, int channel, int status) {
        HdlPacket p = new HdlPacket();
        p.commandType = CommandType.Response_Curtain_Switch_Control;
        p.data = new byte[] { (byte) channel, (byte) status };
        device.treatHDLPacketForDevice(p);
    }

    private static void sendCurtainBroadcast(MW02 device, byte... levelsThenStatuses) {
        HdlPacket p = new HdlPacket();
        p.commandType = CommandType.Broadcast_Status_of_Status_of_Curtain_Switches;
        p.data = levelsThenStatuses;
        device.treatHDLPacketForDevice(p);
    }

    @Test
    void positionIsUnknownBeforeAnyMove() {
        MW02 device = newDevice();
        assertNull(device.getEstimatedPositionShutter1());
    }

    @Test
    void positionStaysUnknownWithoutAKnownDuration() throws InterruptedException {
        MW02 device = newDevice();
        sendCurtainStatus(device, CHANNEL_1, 1); // Open (moving DOWN)
        Thread.sleep(50);
        sendCurtainStatus(device, CHANNEL_1, 0); // Stop
        assertNull(device.getEstimatedPositionShutter1(), "no duration known - can't estimate a position");
    }

    @Test
    void firstMoveThatRunsFullDurationCalibratesToEndStopEvenWithNoPriorBase() throws InterruptedException {
        MW02 device = newDevice();
        sendDuration(device, CHANNEL_1, DURATION_SECONDS);
        sendCurtainStatus(device, CHANNEL_1, 1); // Open (DOWN) - no prior known position
        Thread.sleep((DURATION_SECONDS * 1000) + 200); // run past the full configured duration
        sendCurtainStatus(device, CHANNEL_1, 0); // Stop
        assertEquals(100, device.getEstimatedPositionShutter1(),
                "a full-duration move must hit the physical end-stop (DOWN = 100), regardless of not knowing the starting position");
    }

    @Test
    void partialMoveWithNoPriorBaseStaysUnknown() throws InterruptedException {
        MW02 device = newDevice();
        sendDuration(device, CHANNEL_1, DURATION_SECONDS);
        sendCurtainStatus(device, CHANNEL_1, 1); // Open (DOWN)
        Thread.sleep(100); // well short of the 1s duration
        sendCurtainStatus(device, CHANNEL_1, 0); // Stop
        assertNull(device.getEstimatedPositionShutter1(),
                "an interrupted move with no known starting position has nothing to compute from");
    }

    @Test
    void partialMoveFromAKnownBaseMovesTowardTheNewDirection() throws InterruptedException {
        MW02 device = newDevice();
        sendDuration(device, CHANNEL_1, DURATION_SECONDS);
        // Establish a known base at 100 via a full-duration Open first.
        sendCurtainStatus(device, CHANNEL_1, 1);
        Thread.sleep((DURATION_SECONDS * 1000) + 200);
        sendCurtainStatus(device, CHANNEL_1, 0);
        assertEquals(100, device.getEstimatedPositionShutter1());

        // Now a short, partial Close (UP) from that known base.
        sendCurtainStatus(device, CHANNEL_1, 2);
        Thread.sleep(200);
        sendCurtainStatus(device, CHANNEL_1, 0);
        Integer afterPartialClose = device.getEstimatedPositionShutter1();
        assertTrue(afterPartialClose != null && afterPartialClose < 100 && afterPartialClose > 0,
                "a short move toward UP from 100 should land somewhere between 0 and 100, got: " + afterPartialClose);
    }

    @Test
    void broadcastDecodesParallelArraysNotChannelStatusPairs() {
        // Real capture (device 1039, only channel 1 physically touched): first half of the payload is the
        // "level" array (unused here), second half is the "status" array - NOT a [channel, status] pair
        // like Response_Curtain_Switch_Control uses. This is the exact layout mismatch that caused physical
        // panel moves to be silently misrouted before the fix.
        MW02 device = newDevice();
        sendCurtainBroadcast(device, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00);
        assertEquals(UpDownType.DOWN, device.getUpDownShutter1Status());
        assertEquals(StopMoveType.MOVE, device.getStopMoveShutter1Status());
        assertNull(device.getUpDownShutter2Status(), "channel 2 wasn't touched in this capture - must stay untouched");

        sendCurtainBroadcast(device, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(StopMoveType.STOP, device.getStopMoveShutter1Status());
    }

    @Test
    void broadcastFullDurationMoveCalibratesPositionSameAsDirectControl() throws InterruptedException {
        // The position estimate must be reachable via the broadcast path too, not just
        // Response_Curtain_Switch_Control - both funnel through the same applyCurtainStatus().
        MW02 device = newDevice();
        sendDuration(device, CHANNEL_1, DURATION_SECONDS);
        sendCurtainBroadcast(device, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00); // channel 1 Open
        Thread.sleep((DURATION_SECONDS * 1000) + 200);
        sendCurtainBroadcast(device, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00); // Stop
        assertEquals(100, device.getEstimatedPositionShutter1());
    }

    @Test
    void reversingDirectionMidMoveDoesNotThrowAndStaysInBounds() throws InterruptedException {
        MW02 device = newDevice();
        sendDuration(device, CHANNEL_1, DURATION_SECONDS);
        // Known base at 0 via a full-duration Close first.
        sendCurtainStatus(device, CHANNEL_1, 2);
        Thread.sleep((DURATION_SECONDS * 1000) + 200);
        sendCurtainStatus(device, CHANNEL_1, 0);
        assertEquals(0, device.getEstimatedPositionShutter1());

        // Start Opening, then reverse to Closing before it stops - no Stop in between.
        sendCurtainStatus(device, CHANNEL_1, 1);
        Thread.sleep(150);
        sendCurtainStatus(device, CHANNEL_1, 2);
        Thread.sleep(100);
        sendCurtainStatus(device, CHANNEL_1, 0);

        Integer result = device.getEstimatedPositionShutter1();
        assertTrue(result != null && result >= 0 && result <= 100, "position must stay within bounds: " + result);
    }
}
