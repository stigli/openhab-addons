/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

public class MFH06 extends Device {
    private int channelNr;

    private double temperatureValue;
    private OnOffType uvSwitch1 = null; // Status On/OFf
    private OnOffType uvSwitch2 = null; // Normal Mode
    private OnOffType uvSwitch3 = null; // Day Mode
    private OnOffType uvSwitch4 = null; // Night Mode
    private OnOffType uvSwitch5 = null; // Away Mode
    private OnOffType uvSwitch6 = null; // Timer Mode

    private String floorHeatingTemperaturType;
    private double floorHeatingCurrentTemperatur;
    private OnOffType floorHeatingStatus;
    private EnumFHMode floorHeatingMode;

    private double floorHeatingSetNormalTemperatur;
    private double floorHeatingSetDayTemperatur;
    private double floorHeatingSetNightTemperatur;
    private double floorHeatingSetAwayTemperatur;
    private String floorHeatingTimer;

    /** Device type for this Button Panel (DLP) with AC, Music, Clock, Floor Heating **/
    private DeviceType deviceType = DeviceType.MFH06_432;

    public MFH06(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        if (p.data[0] == channelNr) {
            switch (p.commandType) {
                case Response_Read_Floor_Heating_Status:
                    if (p.data[2] == 1) {
                        setFloorHeatingTemperaturType("F");
                    } else {
                        setFloorHeatingTemperaturType("C");
                    }
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
        if (this.temperatureValue != value) {
            setUpdated(true);
        }
        this.temperatureValue = value;
    }

    /**
     * the Temperature as <code>DecimalType</code>
     */
    public DecimalType getTemperatureValue() {
        BigDecimal temperatureValue = BigDecimal.valueOf(this.temperatureValue);// .setScale(1, RoundingMode.HALF_UP);
        return new DecimalType(temperatureValue);
    }

    public void setUVSwitch1(OnOffType UVSwitch1) {
        if (this.uvSwitch1 != UVSwitch1) {
            setUpdated(true);
        }
        this.uvSwitch1 = UVSwitch1;
    }

    public OnOffType getUVSwitch1() {
        return uvSwitch1;
    }

    public void setUVSwitch2(OnOffType UVSwitch2) {
        if (this.uvSwitch2 != UVSwitch2) {
            setUpdated(true);
        }
        this.uvSwitch2 = UVSwitch2;
    }

    public OnOffType getUVSwitch2() {
        return uvSwitch2;
    }

    public void setUVSwitch3(OnOffType UVSwitch3) {
        if (this.uvSwitch3 != UVSwitch3) {
            setUpdated(true);
        }
        this.uvSwitch3 = UVSwitch3;
    }

    public OnOffType getUVSwitch3() {
        return uvSwitch3;
    }

    public void setUVSwitch4(OnOffType UVSwitch4) {
        if (this.uvSwitch4 != UVSwitch4) {
            setUpdated(true);
        }
        this.uvSwitch4 = UVSwitch4;
    }

    public OnOffType getUVSwitch4() {
        return uvSwitch4;
    }

    public void setUVSwitch5(OnOffType UVSwitch5) {
        if (this.uvSwitch5 != UVSwitch5) {
            setUpdated(true);
        }
        this.uvSwitch5 = UVSwitch5;
    }

    public OnOffType getUVSwitch5() {
        return uvSwitch5;
    }

    public void setUVSwitch6(OnOffType UVSwitch6) {
        if (this.uvSwitch6 != UVSwitch6) {
            setUpdated(true);
        }
        this.uvSwitch6 = UVSwitch6;
    }

    public OnOffType getUVSwitch6() {
        return uvSwitch6;
    }

    public void setFloorHeatingTemperaturType(String FloorHeatingTemperaturType) {
        if (!this.floorHeatingTemperaturType.equals(FloorHeatingTemperaturType)) {
            setUpdated(true);
        }
        this.floorHeatingTemperaturType = FloorHeatingTemperaturType;
    }

    public String getFloorHeatingTemperaturType() {
        return floorHeatingTemperaturType;
    }

    public void setFloorHeatingCurrentTemperatur(double FloorHeatingCurrentTemperatur) {
        if (this.floorHeatingCurrentTemperatur != FloorHeatingCurrentTemperatur) {
            setUpdated(true);
        }
        this.floorHeatingCurrentTemperatur = FloorHeatingCurrentTemperatur;
    }

    public DecimalType getFloorHeatingCurrentTemperatur() {
        BigDecimal floorHeatingCurrentTemperaturValue = BigDecimal.valueOf(this.floorHeatingCurrentTemperatur);
        return new DecimalType(floorHeatingCurrentTemperaturValue);
    }

    public void setFloorHeatingStatus(OnOffType FloorHeatingStatus) {
        if (!this.floorHeatingStatus.equals(FloorHeatingStatus)) {
            setUpdated(true);
        }
        this.floorHeatingStatus = FloorHeatingStatus;
    }

    public OnOffType getFloorHeatingStatus() {
        return floorHeatingStatus;
    }

    public void setFloorHeatingMode(EnumFHMode FloorHeatingMode) {
        if (this.floorHeatingMode != FloorHeatingMode) {
            setUpdated(true);
        }
        this.floorHeatingMode = FloorHeatingMode;
    }

    public EnumFHMode getFloorHeatingMode() {
        return floorHeatingMode;
    }

    public void setFloorHeatingSetNormalTemperatur(double FloorHeatingSetNormalTemperatur) {
        if (this.floorHeatingSetNormalTemperatur != FloorHeatingSetNormalTemperatur) {
            setUpdated(true);
        }
        this.floorHeatingSetNormalTemperatur = FloorHeatingSetNormalTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Normal) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetNormalTemperatur);
        }
    }

    public DecimalType getFloorHeatingSetNormalTemperatur() {
        BigDecimal floorHeatingSetNormalTemperaturValue = BigDecimal.valueOf(this.floorHeatingSetNormalTemperatur);
        return new DecimalType(floorHeatingSetNormalTemperaturValue);
    }

    public void setFloorHeatingSetDayTemperatur(double FloorHeatingSetDayTemperatur) {
        if (this.floorHeatingSetDayTemperatur != FloorHeatingSetDayTemperatur) {
            setUpdated(true);
        }
        this.floorHeatingSetDayTemperatur = FloorHeatingSetDayTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Day) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetDayTemperatur);
        }

        if (this.floorHeatingMode.equals(EnumFHMode.Timer) && "Day".equals(this.floorHeatingTimer)) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetDayTemperatur);
        }
    }

    public DecimalType getFloorHeatingSetDayTemperatur() {
        BigDecimal floorHeatingSetDayTemperaturValue = BigDecimal.valueOf(this.floorHeatingSetDayTemperatur);
        return new DecimalType(floorHeatingSetDayTemperaturValue);
    }

    public void setFloorHeatingSetNightTemperatur(double FloorHeatingSetNightTemperatur) {
        if (this.floorHeatingSetNightTemperatur != FloorHeatingSetNightTemperatur) {
            setUpdated(true);
        }
        this.floorHeatingSetNightTemperatur = FloorHeatingSetNightTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Night) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetNightTemperatur);
        }

        if (this.floorHeatingMode.equals(EnumFHMode.Timer) && "Night".equals(this.floorHeatingTimer)) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetNightTemperatur);
        }
    }

    public DecimalType getFloorHeatingSetNightTemperatur() {
        BigDecimal floorHeatingSetNightTemperaturValue = BigDecimal.valueOf(this.floorHeatingSetNightTemperatur);
        return new DecimalType(floorHeatingSetNightTemperaturValue);
    }

    public void setFloorHeatingSetAwayTemperatur(double FloorHeatingSetAwayTemperatur) {
        if (this.floorHeatingSetAwayTemperatur != FloorHeatingSetAwayTemperatur) {
            setUpdated(true);
        }
        this.floorHeatingSetAwayTemperatur = FloorHeatingSetAwayTemperatur;

        if (this.floorHeatingMode == EnumFHMode.Away) {
            setFloorHeatingCurrentTemperatur(FloorHeatingSetAwayTemperatur);
        }
    }

    public DecimalType getFloorHeatingSetAwayTemperatur() {
        BigDecimal floorHeatingSetAwayTemperaturValue = BigDecimal.valueOf(this.floorHeatingSetAwayTemperatur);
        return new DecimalType(floorHeatingSetAwayTemperaturValue);
    }

    public void setFloorHeatingTimer(String FloorHeatingTimer) {
        if (!this.floorHeatingTimer.equals(FloorHeatingTimer)) {
            setUpdated(true);
        }
        this.floorHeatingTimer = FloorHeatingTimer;
    }

    public String getFloorHeatingTimer() {
        return floorHeatingTimer;
    }
}
