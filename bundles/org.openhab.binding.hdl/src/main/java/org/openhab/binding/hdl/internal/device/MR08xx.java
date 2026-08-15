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
import org.openhab.core.library.types.OnOffType;

/**
 * The MR08xx class contains support channels for device Type MR08 (any A).
 * And how the information on the HDL bus is packet for this device.
 * This is a relay block with 8 relays.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MR08xx extends Device {

    private static final int CHANNEL_COUNT = 8;

    /** Device type for this 12 channel relay **/
    private DeviceType deviceType = DeviceType.MR0816_432;

    /** Relay state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable OnOffType[] relayChannels = new OnOffType[CHANNEL_COUNT + 1];

    public MR08xx(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Response_Read_Status_of_Channels:
            case Response_Read_Current_Level_of_Channels:
                for (int ch = 1; ch <= CHANNEL_COUNT; ch++) {
                    setRelayChannel(ch, p.data[ch] == 0 ? OnOffType.OFF : OnOffType.ON);
                }
                break;
            case Response_Single_Channel_Control:
                // Channels 1-8 are packed as a bitmask in data[4].
                for (int ch = 1; ch <= CHANNEL_COUNT; ch++) {
                    int bit = 1 << (ch - 1);
                    setRelayChannel(ch, (p.data[4] & bit) != 0 ? OnOffType.ON : OnOffType.OFF);
                }
                break;
            case Broadcast_Status_of_Scene:
                // Confirmed via real hardware capture (2026-08-15, on a sibling MR12xx device - see
                // MR12xx.java for the derivation): data[0] = N, the number of area slots this device's
                // channels are divided into (varies per device based on its own HDL Setup Tool config,
                // NOT a fixed value), data[1..N] = per-area active scene number (not needed here),
                // data[N+1] = channel count for this device, data[N+2] = per-channel bitmask, same
                // pattern as Response_Single_Channel_Control above (single byte, <= 8 channels).
                int areaCount = p.data[0];
                int bitmaskStart = areaCount + 2;
                for (int ch = 1; ch <= CHANNEL_COUNT; ch++) {
                    int bit = 1 << (ch - 1);
                    setRelayChannel(ch, (p.data[bitmaskStart] & bit) != 0 ? OnOffType.ON : OnOffType.OFF);
                }
                break;
            case Broadcast_Status_of_Sequence:
                // Known and intentionally ignored: confirmed via the caligo-mentis/smart-bus reference
                // implementation that this is a per-area "which sequence is currently active" broadcast (a
                // sequence is a separate HDL concept from a scene - a timed/ordered chain of scenes), always
                // seen as all-zero (no sequence running) on real hardware. Nothing in this binding has a
                // sequence channel to reflect it.
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
    }

    private void setRelayChannel(int channel, OnOffType value) {
        if (relayChannels[channel] != value) {
            setUpdated(true);
        }
        relayChannels[channel] = value;
    }

    private @Nullable OnOffType getRelayChannel(int channel) {
        return relayChannels[channel];
    }

    public @Nullable OnOffType getRelayCh01State() {
        return getRelayChannel(1);
    }

    public @Nullable OnOffType getRelayCh02State() {
        return getRelayChannel(2);
    }

    public @Nullable OnOffType getRelayCh03State() {
        return getRelayChannel(3);
    }

    public @Nullable OnOffType getRelayCh04State() {
        return getRelayChannel(4);
    }

    public @Nullable OnOffType getRelayCh05State() {
        return getRelayChannel(5);
    }

    public @Nullable OnOffType getRelayCh06State() {
        return getRelayChannel(6);
    }

    public @Nullable OnOffType getRelayCh07State() {
        return getRelayChannel(7);
    }

    public @Nullable OnOffType getRelayCh08State() {
        return getRelayChannel(8);
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this Relay .
     *
     * @param DeviceType as provided by the HDL Packet message
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }
}
