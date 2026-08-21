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
        // Confirmed on real hardware (2026-08-15) that pushing raw UpDownType state makes the Rollershutter
        // item's percentage jump straight to 0%/100% the instant a move starts, instead of tracking the
        // curtain's actual physical travel over time. Not fixed here in the binding - openHAB core already
        // ships a purpose-built solution for exactly this ("dumb" up/down/stop motor, no native percentage
        // feedback): the org.openhab.transform.rollershutterposition add-on's ROLLERSHUTTERPOSITION profile,
        // applied on the Item link (uptime/downtime config), simulates position without any binding code.
        // See the README's "Curtain Position" section for real confirmed travel-time values from
        // CommandType#Get_Curtain_Duration_Request.
        int channel = p.data[0];
        if (channel < 1 || channel > CHANNEL_COUNT) {
            return;
        }
        switch (p.data[1]) {
            case (byte) 0: // Stop
                setStopMoveStatus(channel, StopMoveType.STOP);
                break;
            case (byte) 1: // Open
                setUpDownStatus(channel, UpDownType.DOWN);
                setStopMoveStatus(channel, StopMoveType.MOVE);
                break;
            case (byte) 2: // Close
                setUpDownStatus(channel, UpDownType.UP);
                setStopMoveStatus(channel, StopMoveType.MOVE);
                break;
        }
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
}
