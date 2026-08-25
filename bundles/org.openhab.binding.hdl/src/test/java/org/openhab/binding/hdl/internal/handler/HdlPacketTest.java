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
package org.openhab.binding.hdl.internal.handler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.binding.hdl.internal.device.CommandType;

/**
 * Tests for {@link HdlPacket}'s wire framing (leader/addressing/CRC) - the one class every other part of this
 * binding depends on for correct bus communication, so a framing regression here is the highest-impact class
 * of bug this binding can have.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
class HdlPacketTest {

    private static HdlPacket buildSamplePacket() {
        HdlPacket p = new HdlPacket();
        p.setSourceSubnetID(1);
        p.setSourceDeviceId(34);
        p.setSourceDevice(162); // MPL8_48_FH product code
        p.setCommandType(CommandType.Single_Channel_Control);
        p.setTargetSubnetID(1);
        p.setTargetDeviceId(30);
        p.setData(new byte[] { 1, (byte) 100 });
        return p;
    }

    @Test
    void buildThenParseRoundTripsEveryField() {
        HdlPacket sent = buildSamplePacket();
        byte[] bytes = sent.getBytes();

        HdlPacket parsed = HdlPacket.parse(bytes, bytes.length);

        assertNotNull(parsed);
        assertEquals(1, parsed.sourceSubnetID);
        assertEquals(34, parsed.sourceDeviceID);
        assertEquals(162, parsed.sourceDevice);
        assertEquals(CommandType.Single_Channel_Control, parsed.commandType);
        assertEquals(1, parsed.targetSubnetID);
        assertEquals(30, parsed.targetDeviceID);
        assertArrayEquals(new byte[] { 1, (byte) 100 }, parsed.data);
    }

    @Test
    void sourceIdsAbove127RoundTripWithoutSignExtension() {
        // Regression test for a real bug found and fixed this session: sourceSubnetID/sourceDeviceID/
        // targetSubnetID/targetDeviceID were read from the byte[] into int without an "& 0xff" mask, so any
        // ID >= 128 sign-extended to a negative int (e.g. 199 read back as -57).
        HdlPacket p = new HdlPacket();
        p.setSourceSubnetID(199);
        p.setSourceDeviceId(255);
        p.setSourceDevice(162);
        p.setCommandType(CommandType.Single_Channel_Control);
        p.setTargetSubnetID(200);
        p.setTargetDeviceId(255);
        p.setData(new byte[0]);

        byte[] bytes = p.getBytes();
        HdlPacket parsed = HdlPacket.parse(bytes, bytes.length);

        assertNotNull(parsed);
        assertEquals(199, parsed.sourceSubnetID);
        assertEquals(255, parsed.sourceDeviceID);
        assertEquals(200, parsed.targetSubnetID);
        assertEquals(255, parsed.targetDeviceID);
    }

    @Test
    void parseRejectsDataShorterThanMinimumPacketLength() {
        byte[] tooShort = new byte[20];
        assertNull(HdlPacket.parse(tooShort, tooShort.length));
    }

    @Test
    void parseRejectsWrongMagicString() {
        HdlPacket p = buildSamplePacket();
        byte[] bytes = p.getBytes();
        bytes[4] = 'X'; // corrupt the "HDLMIRACLE" magic string
        assertNull(HdlPacket.parse(bytes, bytes.length));
    }

    @Test
    void parseRejectsCorruptedCrc() {
        HdlPacket p = buildSamplePacket();
        byte[] bytes = p.getBytes();
        bytes[bytes.length - 1] ^= (byte) 0xFF; // flip the last CRC byte
        assertNull(HdlPacket.parse(bytes, bytes.length));
    }

    @Test
    void parseAcceptsEmptyDataPayload() {
        HdlPacket p = new HdlPacket();
        p.setSourceSubnetID(1);
        p.setSourceDeviceId(1);
        p.setSourceDevice(1);
        p.setCommandType(CommandType.Read_Status_of_Channels);
        p.setTargetSubnetID(1);
        p.setTargetDeviceId(1);
        p.setData(new byte[0]);

        byte[] bytes = p.getBytes();
        @Nullable
        HdlPacket parsed = HdlPacket.parse(bytes, bytes.length);

        assertNotNull(parsed);
        assertEquals(0, parsed.data.length);
    }
}
