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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
//import org.eclipse.smarthome.core.library.items.RollershutterItem;

/**
 * The MW02 class contains support channels for device Type MW02.
 * And how the information on the HDL bus is packet for this device.
 * This is a controller to control 2 curtains.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MW02 extends Device {

    private static final int CHANNEL_COUNT = 2;

    /** Device type for Curtain controller for controlling off 3. parts curtains **/
    private DeviceType deviceType = DeviceType.MW02;

    /** Shutter up/down state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable UpDownType[] upDownStates = new UpDownType[CHANNEL_COUNT + 1];

    /** Shutter stop/move state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable StopMoveType[] stopMoveStates = new StopMoveType[CHANNEL_COUNT + 1];

    /**
     * Configured full-travel duration per channel, as reported by the device itself via
     * {@link org.openhab.binding.hdl.internal.device.CommandType#Get_Curtain_Duration_Response}; 1-indexed
     * to match the HDL protocol, index 0 is unused. Unit assumed seconds (not formally confirmed) - see the
     * comment on that CommandType entry.
     **/
    private final @Nullable Integer[] curtainDurations = new Integer[CHANNEL_COUNT + 1];

    /**
     * When the current move started per channel, null when not moving; 1-indexed. Used only for the
     * elapsed-time position estimate below - {@link #upDownStates}/{@link #stopMoveStates} remain the
     * source of truth for direction/moving-or-not.
     **/
    private final @Nullable Instant[] movingSince = new Instant[CHANNEL_COUNT + 1];

    /**
     * Last known/estimated resting position per channel, 0-100 (0=open/UP, 100=closed/DOWN, matching the
     * {@link UpDownType} convention already used elsewhere in this class); 1-indexed. Starts {@code null}
     * (genuinely unknown) rather than assuming 0 like a naive simulation would - only ever set from a real
     * observed move, either a full-duration one (self-calibrating against the physical end-stop, see
     * {@link #getEstimatedPosition}) or computed incrementally from a previously-known position.
     **/
    private final @Nullable Integer[] positions = new Integer[CHANNEL_COUNT + 1];

    public MW02(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        LOGGER.debug("Starting treating package of Commandtype: {}, Of source device: {}.", p.commandType,
                p.sourcedeviceType);

        switch (p.commandType) {
            case Response_Read_Status_of_Curtain_Switch:
            case Response_Curtain_Switch_Control:
                handleCurtainSwitchStatus(p);
                break;
            case Broadcast_Status_of_Status_of_Curtain_Switches:
                handleCurtainSwitchBroadcast(p);
                break;
            case Get_Curtain_Duration_Response:
                // Confirmed via real hardware (2026-08-21): 3-byte payload [channel, duration(2B BE)], not
                // the 4-byte [channel, reserved, duration(2B)] shape a reference implementation assumed.
                int durationChannel = p.data[0];
                if (durationChannel >= 1 && durationChannel <= CHANNEL_COUNT) {
                    int duration = ((p.data[1] & 0xff) << 8) | (p.data[2] & 0xff);
                    setCurtainDuration(durationChannel, duration);
                }
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
    }

    private void handleCurtainSwitchStatus(HdlPacket p) {
        // data[0] holds the 1-based curtain channel (17 = percentage, not yet supported).
        // Position (0-100%) is estimated natively from elapsed time vs. the device's own reported travel
        // duration - see getEstimatedPosition() - rather than relying on the external
        // org.openhab.transform.rollershutterposition add-on's ROLLERSHUTTERPOSITION profile. See the
        // README's "Curtain Position" section.
        int channel = p.data[0];
        if (channel < 1 || channel > CHANNEL_COUNT) {
            return;
        }
        applyCurtainStatus(channel, p.data[1]);
    }

    /**
     * Confirmed via real hardware capture (2026-08-28): the physical panel driving this device's curtain
     * broadcasts its own status change (openHAB previously never saw this - only reflected commands openHAB
     * itself sent). Not a [channel, status] pair like {@link #handleCurtainSwitchStatus} - cross-checked
     * against the {@code smart-bus} reference implementation's 0xE3E4 fixture and validated against three
     * real transitions (Open/Close/Stop, only channel 2 physically moved each time): parallel arrays, first
     * half of the payload is a per-channel "level" (not used here - duplicates "status" for this 2-state,
     * no-percent-feedback device), second half is per-channel "status" in the same 0/1/2 convention used
     * elsewhere in this class.
     */
    private void handleCurtainSwitchBroadcast(HdlPacket p) {
        int size = p.data.length / 2;
        for (int i = 0; i < size; i++) {
            int channel = i + 1;
            if (channel > CHANNEL_COUNT) {
                break;
            }
            applyCurtainStatus(channel, p.data[size + i]);
        }
    }

    private void applyCurtainStatus(int channel, byte status) {
        switch (status) {
            case (byte) 0: // Stop
                stopMoving(channel);
                setStopMoveStatus(channel, StopMoveType.STOP);
                break;
            case (byte) 1: // Open
                startMoving(channel, UpDownType.DOWN);
                setUpDownStatus(channel, UpDownType.DOWN);
                setStopMoveStatus(channel, StopMoveType.MOVE);
                break;
            case (byte) 2: // Close
                startMoving(channel, UpDownType.UP);
                setUpDownStatus(channel, UpDownType.UP);
                setStopMoveStatus(channel, StopMoveType.MOVE);
                break;
        }
    }

    private void startMoving(int channel, UpDownType direction) {
        if (movingSince[channel] != null && upDownStates[channel] != direction) {
            // Reversing direction mid-move - snapshot the current estimate as the new base position before
            // resetting the timer, same approach RollerShutterPositionProfile's own reversal handling uses.
            Integer currentEstimate = getEstimatedPosition(channel);
            if (currentEstimate != null) {
                positions[channel] = currentEstimate;
            }
        }
        movingSince[channel] = Instant.now();
    }

    private void stopMoving(int channel) {
        // movingSince is still set at this point, so this reuses the exact same elapsed-time math as a
        // live estimate - including the unconditional full-duration/end-stop calibration.
        Integer estimate = getEstimatedPosition(channel);
        if (estimate != null) {
            positions[channel] = estimate;
        }
        movingSince[channel] = null;
    }

    /**
     * Elapsed-time position estimate (0-100, 0=open/UP, 100=closed/DOWN) - live value while moving, last
     * known resting value while stopped, or {@code null} if genuinely unknown (no completed, timed move
     * observed yet, or the device hasn't reported its travel duration yet - never guessed).
     * <p>
     * If the current/just-finished move ran for at least the device's full configured travel duration, it
     * must have hit the physical end-stop - this unconditionally calibrates to 0/100 regardless of whether
     * a prior position was known, self-correcting any accumulated drift on every full-travel cycle (same
     * insight as {@code RollerShutterPositionProfile}'s own end-stop handling, which this class's approach
     * is otherwise modeled on - reimplemented natively here instead of relying on that external profile, so
     * it can react to real bus events from the physical panel too, not just openHAB-initiated commands).
     */
    private @Nullable Integer getEstimatedPosition(int channel) {
        Integer base = positions[channel];
        Instant since = movingSince[channel];
        Integer duration = curtainDurations[channel];
        UpDownType direction = upDownStates[channel];
        if (since == null || duration == null || direction == null) {
            return base;
        }
        long elapsedMillis = since.until(Instant.now(), ChronoUnit.MILLIS);
        long durationMillis = duration * 1000L;
        if (elapsedMillis >= durationMillis) {
            return direction == UpDownType.DOWN ? 100 : 0;
        }
        if (base == null) {
            return null;
        }
        int delta = (int) ((elapsedMillis * 100) / durationMillis);
        int newPosition = direction == UpDownType.DOWN ? base + delta : base - delta;
        return Math.max(0, Math.min(100, newPosition));
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this Curtain controller.
     *
     * @param DeviceType as provided
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }

    private void setUpDownStatus(int channel, UpDownType value) {
        if (upDownStates[channel] != value) {
            setUpdated(true);
        }
        upDownStates[channel] = value;
    }

    private @Nullable UpDownType getUpDownStatus(int channel) {
        return upDownStates[channel];
    }

    private void setStopMoveStatus(int channel, StopMoveType value) {
        if (stopMoveStates[channel] != value) {
            setUpdated(true);
        }
        stopMoveStates[channel] = value;
    }

    private @Nullable StopMoveType getStopMoveStatus(int channel) {
        return stopMoveStates[channel];
    }

    /**
     * get the UpDown value for Shutter 1
     */
    public @Nullable UpDownType getUpDownShutter1Status() {
        return getUpDownStatus(1);
    }

    /**
     * get the StopMove value for Shutter 1
     */
    public @Nullable StopMoveType getStopMoveShutter1Status() {
        return getStopMoveStatus(1);
    }

    /**
     * get the UpDown value for Shutter 2
     */
    public @Nullable UpDownType getUpDownShutter2Status() {
        return getUpDownStatus(2);
    }

    /**
     * get the StopMove value for Shutter 2
     */
    public @Nullable StopMoveType getStopMoveShutter2Status() {
        return getStopMoveStatus(2);
    }

    private void setCurtainDuration(int channel, int duration) {
        if (!Integer.valueOf(duration).equals(curtainDurations[channel])) {
            setUpdated(true);
        }
        curtainDurations[channel] = duration;
    }

    /**
     * Configured full-travel duration for Shutter 1, as reported by the device itself.
     */
    public @Nullable Integer getCurtainDurationShutter1() {
        return curtainDurations[1];
    }

    /**
     * Configured full-travel duration for Shutter 2, as reported by the device itself.
     */
    public @Nullable Integer getCurtainDurationShutter2() {
        return curtainDurations[2];
    }

    /**
     * Estimated position (0-100%) for Shutter 1 - see {@link #getEstimatedPosition}.
     */
    public @Nullable Integer getEstimatedPositionShutter1() {
        return getEstimatedPosition(1);
    }

    /**
     * Estimated position (0-100%) for Shutter 2 - see {@link #getEstimatedPosition}.
     */
    public @Nullable Integer getEstimatedPositionShutter2() {
        return getEstimatedPosition(2);
    }

    /**
     * Whether Shutter 1 is currently moving - used by the handler layer to decide whether to run a
     * periodic position-tick job.
     */
    public boolean isMovingShutter1() {
        return movingSince[1] != null;
    }

    /**
     * Whether Shutter 2 is currently moving - used by the handler layer to decide whether to run a
     * periodic position-tick job.
     */
    public boolean isMovingShutter2() {
        return movingSince[2] != null;
    }
}
