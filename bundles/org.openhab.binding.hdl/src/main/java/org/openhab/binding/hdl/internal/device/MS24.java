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

import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.OpenClosedType;

/**
 * The MS24 class contains support channels for device Type MS24.
 * And how the information on the HDL bus is packet for this device.
 * This is a controller with 24 dry contacts
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MS24 extends Device {

    private static final int CHANNEL_COUNT = 24;

    /** Device type for Sensor Input Module **/
    private DeviceType deviceType = DeviceType.MS24;

    /** Dry contact state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable OpenClosedType[] dryContacts = new OpenClosedType[CHANNEL_COUNT + 1];

    /**
     * Whether each channel has responded since the last {@link #resetProbeTracking()} call - separate from
     * {@link #dryContacts}, which persists last-known values across probe attempts/restarts and so can't by
     * itself tell "never responded this probe" apart from "responded earlier, nothing new to report".
     * 1-indexed to match the HDL protocol, index 0 is unused. Added 2026-08-21 to support retrying only the
     * channels that didn't answer a given probe pass (see HdlHandler#sendMs24StatusProbe).
     */
    private final boolean[] respondedThisProbe = new boolean[CHANNEL_COUNT + 1];

    public MS24(DeviceConfiguration c) {
        super(c);
    }

    public void resetProbeTracking() {
        Arrays.fill(respondedThisProbe, false);
    }

    public boolean hasRespondedThisProbe(int channel) {
        return channel >= 1 && channel <= CHANNEL_COUNT && respondedThisProbe[channel];
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Response_Auto_broadcast_Dry_Contact_Status:
            case Response_Read_Dry_Contact_Status:
            case Auto_broadcast_Dry_Contact_Status:
                // data[1] holds the 1-based channel number that changed, data[2] its new state.
                int channel = p.data[1];
                if (channel >= 1 && channel <= CHANNEL_COUNT) {
                    setDryContactValue(channel, p.data[2] == 1 ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                    respondedThisProbe[channel] = true;
                }
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
    }

    private void setDryContactValue(int channel, OpenClosedType value) {
        if (dryContacts[channel] != value) {
            setUpdated(true);
        }
        dryContacts[channel] = value;
    }

    private @Nullable OpenClosedType getDryContactValue(int channel) {
        return dryContacts[channel];
    }

    public @Nullable OpenClosedType getDryContact1Value() {
        return getDryContactValue(1);
    }

    public @Nullable OpenClosedType getDryContact2Value() {
        return getDryContactValue(2);
    }

    public @Nullable OpenClosedType getDryContact3Value() {
        return getDryContactValue(3);
    }

    public @Nullable OpenClosedType getDryContact4Value() {
        return getDryContactValue(4);
    }

    public @Nullable OpenClosedType getDryContact5Value() {
        return getDryContactValue(5);
    }

    public @Nullable OpenClosedType getDryContact6Value() {
        return getDryContactValue(6);
    }

    public @Nullable OpenClosedType getDryContact7Value() {
        return getDryContactValue(7);
    }

    public @Nullable OpenClosedType getDryContact8Value() {
        return getDryContactValue(8);
    }

    public @Nullable OpenClosedType getDryContact9Value() {
        return getDryContactValue(9);
    }

    public @Nullable OpenClosedType getDryContact10Value() {
        return getDryContactValue(10);
    }

    public @Nullable OpenClosedType getDryContact11Value() {
        return getDryContactValue(11);
    }

    public @Nullable OpenClosedType getDryContact12Value() {
        return getDryContactValue(12);
    }

    public @Nullable OpenClosedType getDryContact13Value() {
        return getDryContactValue(13);
    }

    public @Nullable OpenClosedType getDryContact14Value() {
        return getDryContactValue(14);
    }

    public @Nullable OpenClosedType getDryContact15Value() {
        return getDryContactValue(15);
    }

    public @Nullable OpenClosedType getDryContact16Value() {
        return getDryContactValue(16);
    }

    public @Nullable OpenClosedType getDryContact17Value() {
        return getDryContactValue(17);
    }

    public @Nullable OpenClosedType getDryContact18Value() {
        return getDryContactValue(18);
    }

    public @Nullable OpenClosedType getDryContact19Value() {
        return getDryContactValue(19);
    }

    public @Nullable OpenClosedType getDryContact20Value() {
        return getDryContactValue(20);
    }

    public @Nullable OpenClosedType getDryContact21Value() {
        return getDryContactValue(21);
    }

    public @Nullable OpenClosedType getDryContact22Value() {
        return getDryContactValue(22);
    }

    public @Nullable OpenClosedType getDryContact23Value() {
        return getDryContactValue(23);
    }

    public @Nullable OpenClosedType getDryContact24Value() {
        return getDryContactValue(24);
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this 24 ports dry contact inputs.
     *
     * @param DeviceType as provided
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }
}
