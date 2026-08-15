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

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.PercentType;

/**
 * The MDT0601 class contains support channels for device Type MDT0601.
 * And how the information on the HDL bus is packet for this device.
 * This is 6 channel, 1A dimmer.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MDT0601 extends Device {
    // Dimmer 6x1A - Universal
    private @Nullable PercentType dimChannel1 = null;
    private @Nullable PercentType dimChannel2 = null;
    private @Nullable PercentType dimChannel3 = null;
    private @Nullable PercentType dimChannel4 = null;
    private @Nullable PercentType dimChannel5 = null;
    private @Nullable PercentType dimChannel6 = null;

    /**
     * Set when a scene changed this device's channels (see {@link #treatHDLPacketForDevice}) - confirmed on
     * real hardware that {@code Response_Scene_Control}'s payload only reports which scene fired
     * ({@code [area, scene, ...]}), not the resulting channel percentages, so the handler needs to request a
     * fresh status read instead. Consumed (and cleared) via {@link #consumeControlEvent()}.
     */
    private boolean controlEventPending;

    public MDT0601(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Response_Read_Status_of_Channels:
                setDimChannel1(PercentType.valueOf(Integer.toString(p.data[1])));
                setDimChannel2(PercentType.valueOf(Integer.toString(p.data[2])));
                setDimChannel3(PercentType.valueOf(Integer.toString(p.data[3])));
                setDimChannel4(PercentType.valueOf(Integer.toString(p.data[4])));
                setDimChannel5(PercentType.valueOf(Integer.toString(p.data[5])));
                setDimChannel6(PercentType.valueOf(Integer.toString(p.data[6])));
                break;
            case Response_Read_Current_Level_of_Channels:
                setDimChannel1(PercentType.valueOf(Integer.toString(p.data[1])));
                setDimChannel2(PercentType.valueOf(Integer.toString(p.data[2])));
                setDimChannel3(PercentType.valueOf(Integer.toString(p.data[3])));
                setDimChannel4(PercentType.valueOf(Integer.toString(p.data[4])));
                setDimChannel5(PercentType.valueOf(Integer.toString(p.data[5])));
                setDimChannel6(PercentType.valueOf(Integer.toString(p.data[6])));
                break;
            case Response_Single_Channel_Control:
                switch (p.data[0]) {
                    case 1:
                        int dimCh1 = p.data[2];
                        setDimChannel1(PercentType.valueOf(Integer.toString(dimCh1)));
                        break;
                    case 2:
                        int dimCh2 = p.data[2];
                        setDimChannel2(PercentType.valueOf(Integer.toString(dimCh2)));
                        break;
                    case 3:
                        int dimCh3 = p.data[2];
                        setDimChannel3(PercentType.valueOf(Integer.toString(dimCh3)));
                        break;
                    case 4:
                        int dimCh4 = p.data[2];
                        setDimChannel4(PercentType.valueOf(Integer.toString(dimCh4)));
                        break;
                    case 5:
                        int dimCh5 = p.data[2];
                        setDimChannel5(PercentType.valueOf(Integer.toString(dimCh5)));
                        break;
                    case 6:
                        int dimCh6 = p.data[2];
                        setDimChannel6(PercentType.valueOf(Integer.toString(dimCh6)));
                        break;
                    default:
                        LOGGER.debug("For type: {}, CommandType: {}, does not support channel: {}.", p.sourcedeviceType,
                                p.commandType, p.data[0]);
                        break;
                }
                break;
            case Broadcast_Status_of_Scene:
                // Header confirmed via real hardware capture (2026-08-15, on a sibling MR12xx device -
                // see MR12xx.java for the derivation): data[0] = N, the number of area slots this
                // device's channels are divided into (varies per device, NOT a fixed value), data[1..N]
                // = per-area active scene number (not needed here), data[N+1] = channel count for this
                // device. Percentage-per-channel encoding of the values themselves (matching
                // Response_Read_Status_of_Channels above) is NOT yet confirmed against a real dimmer
                // device's scene broadcast specifically.
                int areaCount = p.data[0];
                int valuesStart = areaCount + 2;
                setDimChannel1(PercentType.valueOf(Integer.toString(p.data[valuesStart])));
                setDimChannel2(PercentType.valueOf(Integer.toString(p.data[valuesStart + 1])));
                setDimChannel3(PercentType.valueOf(Integer.toString(p.data[valuesStart + 2])));
                setDimChannel4(PercentType.valueOf(Integer.toString(p.data[valuesStart + 3])));
                setDimChannel5(PercentType.valueOf(Integer.toString(p.data[valuesStart + 4])));
                setDimChannel6(PercentType.valueOf(Integer.toString(p.data[valuesStart + 5])));
                break;
            case Broadcast_Status_of_Sequence:
                // Known and intentionally ignored: confirmed via the caligo-mentis/smart-bus reference
                // implementation that this is a per-area "which sequence is currently active" broadcast (a
                // sequence is a separate HDL concept from a scene - a timed/ordered chain of scenes), always
                // seen as all-zero (no sequence running) on real hardware. Nothing in this binding has a
                // sequence channel to reflect it.
                break;
            case Response_Scene_Control:
                // Confirmed on real hardware (2026-08-15): data[1] is the scene number that just became
                // active in this device's area (matching Response_Read_Status_of_Scene's [area, scene]
                // shape), not a channel percentage - the resulting brightness isn't in this payload at all,
                // so just flag it for the handler to request a fresh status read.
                controlEventPending = true;
                setUpdated(true);
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
    }

    /**
     * Returns whether a scene changed this device's channels (see {@link #treatHDLPacketForDevice}) since
     * the last call, and clears the flag.
     */
    public boolean consumeControlEvent() {
        boolean pending = controlEventPending;
        controlEventPending = false;
        return pending;
    }

    public void setDimChannel1(@Nullable PercentType DimChannel1) {
        if (!Objects.equals(this.dimChannel1, DimChannel1)) {
            setUpdated(true);
        }
        this.dimChannel1 = DimChannel1;
    }

    public @Nullable PercentType getDimChannel1State() {
        return dimChannel1;
    }

    public void setDimChannel2(@Nullable PercentType DimChannel2) {
        if (!Objects.equals(this.dimChannel2, DimChannel2)) {
            setUpdated(true);
        }
        this.dimChannel2 = DimChannel2;
    }

    public @Nullable PercentType getDimChannel2State() {
        return dimChannel2;
    }

    public void setDimChannel3(@Nullable PercentType DimChannel3) {
        if (!Objects.equals(this.dimChannel3, DimChannel3)) {
            setUpdated(true);
        }
        this.dimChannel3 = DimChannel3;
    }

    public @Nullable PercentType getDimChannel3State() {
        return dimChannel3;
    }

    public void setDimChannel4(@Nullable PercentType DimChannel4) {
        if (!Objects.equals(this.dimChannel4, DimChannel4)) {
            setUpdated(true);
        }
        this.dimChannel4 = DimChannel4;
    }

    public @Nullable PercentType getDimChannel4State() {
        return dimChannel4;
    }

    public void setDimChannel5(@Nullable PercentType DimChannel5) {
        if (!Objects.equals(this.dimChannel5, DimChannel5)) {
            setUpdated(true);
        }
        this.dimChannel5 = DimChannel5;
    }

    public @Nullable PercentType getDimChannel5State() {
        return dimChannel5;
    }

    public void setDimChannel6(@Nullable PercentType DimChannel6) {
        if (!Objects.equals(this.dimChannel6, DimChannel6)) {
            setUpdated(true);
        }
        this.dimChannel6 = DimChannel6;
    }

    public @Nullable PercentType getDimChannel6State() {
        return dimChannel6;
    }

    @Override
    public DeviceType getType() {
        return DeviceType.MDT0601_233;
    }
}
