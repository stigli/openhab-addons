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

import java.math.BigDecimal;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.HdlBindingConstants.EnumFHMode;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;

/**
 * The MFH06 class contains support channels for device Type MFH06.
 * And how the information on the HDL bus is packet for this device.
 * This is a button panel which contains temperature measurement.
 *
 * @author stigla - Initial contribution
 */

@NonNullByDefault
public class MFH06 extends Device {
    private int channelNr;

    /**
     * These are @Nullable and stay null until real data has actually been received: HdlHandler relies on
     * being able to tell "never received" apart from "received as 0" both when pushing channel state and
     * when guarding against sending a command before the real device state is known (see MPL848FH, which
     * had the same bug when these were plain primitive doubles defaulting to 0.0).
     **/
    private @Nullable Double temperatureValue;
    private @Nullable OnOffType uvSwitch1 = null; // Status On/OFf
    private @Nullable OnOffType uvSwitch2 = null; // Normal Mode
    private @Nullable OnOffType uvSwitch3 = null; // Day Mode
    private @Nullable OnOffType uvSwitch4 = null; // Night Mode
    private @Nullable OnOffType uvSwitch5 = null; // Away Mode
    private @Nullable OnOffType uvSwitch6 = null; // Timer Mode

    private @Nullable String floorHeatingTemperaturType;
    private @Nullable Double floorHeatingCurrentTemperatur;
    private @Nullable OnOffType floorHeatingStatus;
    private @Nullable EnumFHMode floorHeatingMode;

    private @Nullable Double floorHeatingSetNormalTemperatur;
    private @Nullable Double floorHeatingSetDayTemperatur;
    private @Nullable Double floorHeatingSetNightTemperatur;
    private @Nullable Double floorHeatingSetAwayTemperatur;
    private @Nullable String floorHeatingTimer;

    /** Device type for this Button Panel (DLP) with AC, Music, Clock, Floor Heating **/
    private DeviceType deviceType = DeviceType.MFH06_432;

    public MFH06(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        if (p.data[0] == channelNr) {
            switch (p.commandType) {
                case Response_Read_Floor_Heating_Status:
                    setFloorHeatingTemperaturType(p.data[2] == 1 ? "F" : "C");
                    if (p.data[3] == 1) {
                        setFloorHeatingMode(EnumFHMode.Normal);
                    } else if (p.data[3] == 2) {
                        setFloorHeatingMode(EnumFHMode.Day);
                    } else if (p.data[3] == 3) {
                        setFloorHeatingMode(EnumFHMode.Night);
                    } else if (p.data[3] == 4) {
                        setFloorHeatingMode(EnumFHMode.Away);
                    } else if (p.data[3] == 5) {
                        setFloorHeatingMode(EnumFHMode.Timer);
                    }

                    if (p.data[8] == 1) {
                        setFloorHeatingTimer("Night");
                    } else {
                        setFloorHeatingTimer("Day");
                    }

                    // This has to be done last so Current Temperature can be set correctly, since current temperature
                    // needs
                    // to know what FloorHeatingMode Floor heating module is in.
                    setFloorHeatingSetNormalTemperatur(p.data[4]);
                    setFloorHeatingSetDayTemperatur(p.data[5]);
                    setFloorHeatingSetNightTemperatur(p.data[6]);
                    setFloorHeatingSetAwayTemperatur(p.data[7]);
                    break;
                default:
                    LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                    break;
            }
        } else {
            LOGGER.debug("For type: {}, Channel number in in HDL packet is {}, but in config it is {}",
                    p.sourcedeviceType, p.data[0], channelNr);
        }
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this thermostat.
     *
     * @param DeviceType as provided by the HDL Packet
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }

    /**
     * Sets the actual channel number for this floor heating module panel.
     *
     * @param value the channel number from config
     */

    public void setchannelNrValue(int value) {
        this.channelNr = value;
    }

    /**
     * the Temperature as <code>DecimalType</code>
     */
    public int getChannelNrValue() {
        return channelNr;
    }

    /**
     * Sets the actual temperature for this floor heating module panel.
     *
     * @param value the actual temperature value as provided
     */

    public void setTemperatureValue(double value) {
        if (!Objects.equals(this.temperatureValue, value)) {
            setUpdated(true);
        }
        this.temperatureValue = value;
    }

    /**
     * the Temperature as <code>DecimalType</code>, or null if not yet received.
     */
    public @Nullable DecimalType getTemperatureValue() {
        Double value = this.temperatureValue;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setUVSwitch1(OnOffType UVSwitch1) {
        if (!Objects.equals(this.uvSwitch1, UVSwitch1)) {
            setUpdated(true);
        }
        this.uvSwitch1 = UVSwitch1;
    }

    public @Nullable OnOffType getUVSwitch1() {
        return uvSwitch1;
    }

    public void setUVSwitch2(OnOffType UVSwitch2) {
        if (!Objects.equals(this.uvSwitch2, UVSwitch2)) {
            setUpdated(true);
        }
        this.uvSwitch2 = UVSwitch2;
    }

    public @Nullable OnOffType getUVSwitch2() {
        return uvSwitch2;
    }

    public void setUVSwitch3(OnOffType UVSwitch3) {
        if (!Objects.equals(this.uvSwitch3, UVSwitch3)) {
            setUpdated(true);
        }
        this.uvSwitch3 = UVSwitch3;
    }

    public @Nullable OnOffType getUVSwitch3() {
        return uvSwitch3;
    }

    public void setUVSwitch4(OnOffType UVSwitch4) {
        if (!Objects.equals(this.uvSwitch4, UVSwitch4)) {
            setUpdated(true);
        }
        this.uvSwitch4 = UVSwitch4;
    }

    public @Nullable OnOffType getUVSwitch4() {
        return uvSwitch4;
    }

    public void setUVSwitch5(OnOffType UVSwitch5) {
        if (!Objects.equals(this.uvSwitch5, UVSwitch5)) {
            setUpdated(true);
        }
        this.uvSwitch5 = UVSwitch5;
    }

    public @Nullable OnOffType getUVSwitch5() {
        return uvSwitch5;
    }

    public void setUVSwitch6(OnOffType UVSwitch6) {
        if (!Objects.equals(this.uvSwitch6, UVSwitch6)) {
            setUpdated(true);
        }
        this.uvSwitch6 = UVSwitch6;
    }

    public @Nullable OnOffType getUVSwitch6() {
        return uvSwitch6;
    }

    public void setFloorHeatingTemperaturType(String FloorHeatingTemperaturType) {
        if (!Objects.equals(this.floorHeatingTemperaturType, FloorHeatingTemperaturType)) {
            setUpdated(true);
        }
        this.floorHeatingTemperaturType = FloorHeatingTemperaturType;
    }

    public @Nullable String getFloorHeatingTemperaturType() {
        return floorHeatingTemperaturType;
    }

    public void setFloorHeatingCurrentTemperatur(double FloorHeatingCurrentTemperatur) {
        if (!Objects.equals(this.floorHeatingCurrentTemperatur, FloorHeatingCurrentTemperatur)) {
            setUpdated(true);
        }
        this.floorHeatingCurrentTemperatur = FloorHeatingCurrentTemperatur;
    }

    public @Nullable DecimalType getFloorHeatingCurrentTemperatur() {
        Double value = this.floorHeatingCurrentTemperatur;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setFloorHeatingStatus(OnOffType FloorHeatingStatus) {
        if (!Objects.equals(this.floorHeatingStatus, FloorHeatingStatus)) {
            setUpdated(true);
        }
        this.floorHeatingStatus = FloorHeatingStatus;
    }

    public @Nullable OnOffType getFloorHeatingStatus() {
        return floorHeatingStatus;
    }

    public void setFloorHeatingMode(EnumFHMode FloorHeatingMode) {
        if (!Objects.equals(this.floorHeatingMode, FloorHeatingMode)) {
            setUpdated(true);
        }
        this.floorHeatingMode = FloorHeatingMode;
    }

    public @Nullable EnumFHMode getFloorHeatingMode() {
        return floorHeatingMode;
    }

    public void setFloorHeatingSetNormalTemperatur(double FloorHeatingSetNormalTemperatur) {
        if (!Objects.equals(this.floorHeatingSetNormalTemperatur, FloorHeatingSetNormalTemperatur)) {
            setUpdated(true);
        }
        this.floorHeatingSetNormalTemperatur = FloorHeatingSetNormalTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Normal) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetNormalTemperatur);
        }
    }

    public @Nullable DecimalType getFloorHeatingSetNormalTemperatur() {
        Double value = this.floorHeatingSetNormalTemperatur;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setFloorHeatingSetDayTemperatur(double FloorHeatingSetDayTemperatur) {
        if (!Objects.equals(this.floorHeatingSetDayTemperatur, FloorHeatingSetDayTemperatur)) {
            setUpdated(true);
        }
        this.floorHeatingSetDayTemperatur = FloorHeatingSetDayTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Day) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetDayTemperatur);
        }

        if (Objects.equals(this.floorHeatingMode, EnumFHMode.Timer) && "Day".equals(this.floorHeatingTimer)) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetDayTemperatur);
        }
    }

    public @Nullable DecimalType getFloorHeatingSetDayTemperatur() {
        Double value = this.floorHeatingSetDayTemperatur;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setFloorHeatingSetNightTemperatur(double FloorHeatingSetNightTemperatur) {
        if (!Objects.equals(this.floorHeatingSetNightTemperatur, FloorHeatingSetNightTemperatur)) {
            setUpdated(true);
        }
        this.floorHeatingSetNightTemperatur = FloorHeatingSetNightTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Night) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetNightTemperatur);
        }

        if (Objects.equals(this.floorHeatingMode, EnumFHMode.Timer) && "Night".equals(this.floorHeatingTimer)) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetNightTemperatur);
        }
    }

    public @Nullable DecimalType getFloorHeatingSetNightTemperatur() {
        Double value = this.floorHeatingSetNightTemperatur;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setFloorHeatingSetAwayTemperatur(double FloorHeatingSetAwayTemperatur) {
        if (!Objects.equals(this.floorHeatingSetAwayTemperatur, FloorHeatingSetAwayTemperatur)) {
            setUpdated(true);
        }
        this.floorHeatingSetAwayTemperatur = FloorHeatingSetAwayTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Away) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetAwayTemperatur);
        }
    }

    public @Nullable DecimalType getFloorHeatingSetAwayTemperatur() {
        Double value = this.floorHeatingSetAwayTemperatur;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setFloorHeatingTimer(String FloorHeatingTimer) {
        if (!Objects.equals(this.floorHeatingTimer, FloorHeatingTimer)) {
            setUpdated(true);
        }
        this.floorHeatingTimer = FloorHeatingTimer;
    }

    public @Nullable String getFloorHeatingTimer() {
        return floorHeatingTimer;
    }
}
