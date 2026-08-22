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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.HdlBindingConstants.EnumFHMode;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;

/**
 * The MPL8_48_FH class contains support channels for device Type MPL8.
 * And how the information on the HDL bus is packet for this device.
 * This is a button panel which contains temperature measurement.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MPL848FH extends Device implements UniversalSwitchDevice {
    /**
     * All the temperature-ish fields below are @Nullable and stay null until real data has actually been
     * received for them: several call sites (state-push in HdlHandler#onDeviceStateChanged, and the
     * "don't send a command until we know the real state" guard in HdlHandler#handleCommand) rely on
     * being able to tell "never received" apart from "received as 0".
     **/
    private @Nullable Double temperatureValue;

    /** Universal switch state, keyed by switch number (see {@link UniversalSwitchDevice}). **/
    private final Map<Integer, OnOffType> uvSwitches = new HashMap<>();

    private static final Pattern Z_AUDIO_COMMAND_PATTERN = Pattern.compile("^\\*Z\\d+(.+?)\\r?$");

    /** Last command the panel's Music tab sent its onboard Z-Audio engine, e.g. "ON", "SRC+". **/
    private @Nullable String musicCommand;

    // Floor Heating
    private @Nullable String floorHeatingTemperaturType;
    private @Nullable Double floorHeatingCurrentTemperatur;
    private @Nullable OnOffType floorHeatingStatus = null;
    private @Nullable EnumFHMode floorHeatingMode;
    private @Nullable Double floorHeatingSetNormalTemperatur;
    private @Nullable Double floorHeatingSetDayTemperatur;
    private @Nullable Double floorHeatingSetNightTemperatur;
    private @Nullable Double floorHeatingSetAwayTemperatur;
    private @Nullable String floorHeatingTimer;

    // AC
    private @Nullable String acFanSpeed;
    private @Nullable String acMode;
    private @Nullable Double acCoolingTemp;
    private @Nullable Double acHeatTemp;
    private @Nullable Double acAutoTemp;
    private @Nullable Double acDryTemp;
    private @Nullable Double acCurrentTemp;
    private @Nullable OnOffType acPower;

    // Panel settings - confirmed via the official "HDL-BUS Pro operation codes" reference doc
    // (2026-08-22), not yet independently confirmed against real hardware traffic for these specific
    // fields (unlike the AC/Floor Heating types above, which were already confirmed).
    private @Nullable OnOffType panelKeyLock;
    private @Nullable OnOffType lockAC;
    private @Nullable OnOffType setupPageLock;
    private @Nullable OnOffType lcdBacklightStatus;
    private @Nullable Double backlight;
    private @Nullable Double statusLight;

    /** Device type for this Button Panel (DLP) with AC, Music, Clock, Floor Heating **/
    private DeviceType deviceType = DeviceType.MPL8_48_FH;

    public MPL848FH(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Broadcast_Temperature:
                String inToHex = String.format("%02X", p.data[5]) + String.format("%02X", p.data[4])
                        + String.format("%02X", p.data[3]) + String.format("%02X", p.data[2]);

                Long i = Long.valueOf(inToHex, 16);
                Float tempfloat = Float.intBitsToFloat(i.intValue());
                setTemperatureValue(tempfloat);
                break;
            case Response_Panel_Control:
                switch (p.data[0]) {
                    case (byte) 2:// Lock key of panel
                        setPanelKeyLock(p.data[1] == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case (byte) 3:// AC power
                        setACPower(p.data[1] == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case (byte) 4:// Cooling Temp
                        setACCoolingTemperatur(p.data[1]);
                        break;
                    case (byte) 5:// Fan Speed
                        if (p.data[1] == 0) {
                            setACFanSpeed("Auto");
                        } else if (p.data[1] == 1) {
                            setACFanSpeed("High");
                        } else if (p.data[1] == 2) {
                            setACFanSpeed("Medium");
                        } else if (p.data[1] == 3) {
                            setACFanSpeed("Low");
                        }
                        break;
                    case (byte) 6:// AC Mode
                        if (p.data[1] == 0) {
                            setACMode("Cooling");
                        } else if (p.data[1] == 1) {
                            setACMode("Heating");
                        } else if (p.data[1] == 2) {
                            setACMode("Fan");
                        } else if (p.data[1] == 3) {
                            setACMode("Auto");
                        } else if (p.data[1] == 4) {
                            setACMode("Dehumidfy");
                        }
                        break;
                    case (byte) 7:// Heat Temp
                        setACHeatTemperatur(p.data[1]);
                        break;
                    case (byte) 8:// Auto Temp
                        setACAutoTemperatur(p.data[1]);
                        break;

                    case (byte) 11:// LCD backlight status
                        setLCDBacklightStatus(p.data[1] == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case (byte) 12:// Lock AC
                        setLockAC(p.data[1] == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case (byte) 13:// Backlight (0-100)
                        setBacklight(p.data[1]);
                        break;
                    case (byte) 14:// Status light (0-100)
                        setStatusLight(p.data[1]);
                        break;
                    case (byte) 19:
                        setACDryTemperatur(p.data[1]);
                        break;
                    case (byte) 20:
                        if (p.data[1] == 1) {
                            setFloorHeatingStatus(OnOffType.ON);
                        } else {
                            setFloorHeatingStatus(OnOffType.OFF);
                        }
                        break;
                    case (byte) 21:
                        if (p.data[1] == 1) {
                            setFloorHeatingMode(EnumFHMode.Normal);
                        } else if (p.data[1] == 2) {
                            setFloorHeatingMode(EnumFHMode.Day);
                        } else if (p.data[1] == 3) {
                            setFloorHeatingMode(EnumFHMode.Night);
                        } else if (p.data[1] == 4) {
                            setFloorHeatingMode(EnumFHMode.Away);
                        } else if (p.data[1] == 5) {
                            setFloorHeatingMode(EnumFHMode.Timer);
                        }
                        break;
                    case (byte) 24:// Setup page lock
                        setSetupPageLock(p.data[1] == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case (byte) 25:
                        setFloorHeatingSetNormalTemperatur(p.data[1]);
                        break;
                    case (byte) 26:
                        setFloorHeatingSetDayTemperatur(p.data[1]);
                        break;
                    case (byte) 27:
                        setFloorHeatingSetNightTemperatur(p.data[1]);
                        break;
                    case (byte) 28:
                        setFloorHeatingSetAwayTemperatur(p.data[1]);
                        break;
                    case (byte) 29:
                        // Navigation in panel.
                        break;
                    default:
                        LOGGER.debug("For type: {}, Unhandled Byte in Response Panel Control: {}.", p.sourcedeviceType,
                                p.data[0]);
                        break;
                }

                break;
            case Response_Read_Floor_Heating_Status_DLP:
                if (p.data[0] == 1) {
                    setFloorHeatingTemperaturType("F");
                } else {
                    setFloorHeatingTemperaturType("C");
                }

                if (p.data[2] == 1) {
                    setFloorHeatingStatus(OnOffType.ON);
                } else {
                    setFloorHeatingStatus(OnOffType.OFF);
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

                // This has to be done last so Current Temperature can be set correctly, since current temperature needs
                // to know what FloorHeatingMode DLP is in.
                setFloorHeatingSetNormalTemperatur(p.data[4]);
                setFloorHeatingSetDayTemperatur(p.data[5]);
                setFloorHeatingSetNightTemperatur(p.data[6]);
                setFloorHeatingSetAwayTemperatur(p.data[7]);

                break;
            case Response_Control_Floor_Heating_Status_DLP:
                if (p.data[1] == 1) {
                    setFloorHeatingTemperaturType("F");
                } else {
                    setFloorHeatingTemperaturType("C");
                }
                if (p.data[2] == 1) {
                    setFloorHeatingStatus(OnOffType.ON);
                } else {
                    setFloorHeatingStatus(OnOffType.OFF);
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

                setFloorHeatingSetNormalTemperatur(p.data[4]);
                setFloorHeatingSetDayTemperatur(p.data[5]);
                setFloorHeatingSetNightTemperatur(p.data[6]);
                setFloorHeatingSetAwayTemperatur(p.data[7]);

                break;
            case Response_UV_Switch_Control:
                setUVSwitch(p.data[0] & 0xff, p.data[1] == 1 ? OnOffType.ON : OnOffType.OFF);
                break;
            case Broadcast_Status_of_Status_of_UV_Switches:
                // Confirmed via the caligo-mentis/smart-bus reference implementation (same source already
                // confirmed twice on real hardware this session, for the relay Scene bitmask and the
                // Sequence broadcast) - not yet independently confirmed against real MPL8_48_FH traffic
                // specifically: data[0] = N, the number of UV switches reported, data[1..N] = one status
                // byte per switch (0 = off, non-zero = on), in order for switches 1..N.
                int uvSwitchCount = p.data[0] & 0xff;
                for (int switchNumber = 1; switchNumber <= uvSwitchCount; switchNumber++) {
                    setUVSwitch(switchNumber, p.data[switchNumber] != 0 ? OnOffType.ON : OnOffType.OFF);
                }
                break;
            case Scene_Control:
            case Single_Channel_Control:
                // Known and intentionally ignored: this panel's keys are programmed (via the HDL Buspro
                // Setup Tool) to directly drive another device instead of reporting their own state - same
                // situation already confirmed on real hardware for MPT0448's touch buttons (see
                // MPT0448#treatHDLPacketForDevice). Nothing in this device's own state to update from these.
                break;
            case Read_Z_audio_Current_Status:
            case Response_Read_Z_audio_Current_Status:
                // Confirmed via real hardware capture (2026-08-18): the panel's Music tab Play/Stop/source
                // buttons don't go through the Universal Switch config in the HDL Setup Tool's CMD list at
                // all (that appears to be inert for these specific buttons, regardless of Mode) - instead
                // the panel always drives its onboard Z-Audio engine directly with a plain-text
                // "*Z<zone><command>\r" string, e.g. "*Z1ON" for Play, "*Z1SRC+" seen tied to a source
                // change, and "*Z1STATUS?" as a repeating ~1-2s background poll (filtered out below as
                // noise, not a real button press). The full command vocabulary beyond ON/SRC+ is not yet
                // mapped - this just exposes whatever comes through verbatim.
                parseZAudioCommand(p.data);
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
    }

    private void parseZAudioCommand(byte[] data) {
        String raw = new String(data, StandardCharsets.US_ASCII);
        Matcher matcher = Z_AUDIO_COMMAND_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            LOGGER.debug("Music command from {} did not match the expected \"*Z<zone><command>\" format.", getType());
            return;
        }
        String command = matcher.group(1);
        if ("STATUS?".equals(command)) {
            // Repeating background poll while the Music tab is open, not a real button press - not
            // exposed as a channel update, it would spam every 1-2 seconds for no reason.
            return;
        }
        setMusicCommand(command);
    }

    /**
     * Always marks the device updated, even if the command repeats - each one represents a distinct
     * button press, and a rule reacting to it needs every press to fire, not just the first of a run of
     * identical ones.
     */
    public void setMusicCommand(String command) {
        this.musicCommand = command;
        setUpdated(true);
    }

    public @Nullable String getMusicCommand() {
        return musicCommand;
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this thermostat.
     *
     * @param DeviceType as provided by the C message
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }

    /**
     * Sets the actual temperature for this DLP panel.
     *
     * @param value the actual temperature value as provided
     */

    public void setTemperatureValue(double value) {
        double rounded = roundToHalf(value);
        if (!Objects.equals(this.temperatureValue, rounded)) {
            setUpdated(true);
        }
        this.temperatureValue = rounded;
    }

    /**
     * the Temperature as <code>DecimalType</code>, or null if not yet received.
     */
    public @Nullable DecimalType getTemperatureValue() {
        Double value = this.temperatureValue;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setUVSwitch(int switchNumber, OnOffType value) {
        if (!Objects.equals(uvSwitches.get(switchNumber), value)) {
            setUpdated(true);
        }
        uvSwitches.put(switchNumber, value);
    }

    @Override
    public @Nullable OnOffType getUVSwitchState(int switchNumber) {
        return uvSwitches.get(switchNumber);
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

    public void setFloorHeatingStatus(@Nullable OnOffType FloorHeatingStatus) {
        if (!Objects.equals(this.floorHeatingStatus, FloorHeatingStatus)) {
            setUpdated(true);
        }
        this.floorHeatingStatus = FloorHeatingStatus;
    }

    public @Nullable OnOffType getFloorHeatingStatus() {
        return floorHeatingStatus;
    }

    public void setFloorHeatingMode(@Nullable EnumFHMode FloorHeatingMode) {
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

        if (this.floorHeatingMode == EnumFHMode.Timer && "Day".equals(this.floorHeatingTimer)) {
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

        if (this.floorHeatingMode == EnumFHMode.Timer && "Night".equals(this.floorHeatingTimer)) {
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

    // AC

    public void setACFanSpeed(String ACFanSpeed) {
        if (!Objects.equals(this.acFanSpeed, ACFanSpeed)) {
            setUpdated(true);
        }
        this.acFanSpeed = ACFanSpeed;
    }

    public @Nullable String getACFanSpeed() {
        return acFanSpeed;
    }

    public void setACMode(String ACMode) {
        if (!Objects.equals(this.acMode, ACMode)) {
            setUpdated(true);
        }
        this.acMode = ACMode;
    }

    public @Nullable String getACMode() {
        return acMode;
    }

    public void setACCoolingTemperatur(double ACCoolingTemperatur) {
        if (!Objects.equals(this.acCoolingTemp, ACCoolingTemperatur)) {
            setUpdated(true);
        }
        this.acCoolingTemp = ACCoolingTemperatur;

        if ("Cooling".equals(this.acMode)) {
            setACCurrentTemperatur(ACCoolingTemperatur);
        }
    }

    public @Nullable DecimalType getACCoolingTemperatur() {
        Double value = this.acCoolingTemp;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setACHeatTemperatur(double ACHeatTemperatur) {
        if (!Objects.equals(this.acHeatTemp, ACHeatTemperatur)) {
            setUpdated(true);
        }
        this.acHeatTemp = ACHeatTemperatur;

        if ("Heating".equals(this.acMode)) {
            setACCurrentTemperatur(ACHeatTemperatur);
        }
    }

    public @Nullable DecimalType getACHeatTemperatur() {
        Double value = this.acHeatTemp;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setACAutoTemperatur(double ACAutoTemperatur) {
        if (!Objects.equals(this.acAutoTemp, ACAutoTemperatur)) {
            setUpdated(true);
        }
        this.acAutoTemp = ACAutoTemperatur;

        if ("Auto".equals(this.acMode)) {
            setACCurrentTemperatur(ACAutoTemperatur);
        }
    }

    public @Nullable DecimalType getACAutoTemperatur() {
        Double value = this.acAutoTemp;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setACDryTemperatur(double ACDryTemperatur) {
        if (!Objects.equals(this.acDryTemp, ACDryTemperatur)) {
            setUpdated(true);
        }
        this.acDryTemp = ACDryTemperatur;

        if ("Dehumidfy".equals(this.acMode)) {
            setACCurrentTemperatur(ACDryTemperatur);
        }
    }

    public @Nullable DecimalType getACDryTemperatur() {
        Double value = this.acDryTemp;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setACCurrentTemperatur(double ACCurrentTemperatur) {
        if (!Objects.equals(this.acCurrentTemp, ACCurrentTemperatur)) {
            setUpdated(true);
        }
        this.acCurrentTemp = ACCurrentTemperatur;
    }

    public @Nullable DecimalType getACCurrentTemperatur() {
        Double value = this.acCurrentTemp;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setACPower(OnOffType value) {
        if (!Objects.equals(this.acPower, value)) {
            setUpdated(true);
        }
        this.acPower = value;
    }

    public @Nullable OnOffType getACPower() {
        return acPower;
    }

    public void setPanelKeyLock(OnOffType value) {
        if (!Objects.equals(this.panelKeyLock, value)) {
            setUpdated(true);
        }
        this.panelKeyLock = value;
    }

    public @Nullable OnOffType getPanelKeyLock() {
        return panelKeyLock;
    }

    public void setLockAC(OnOffType value) {
        if (!Objects.equals(this.lockAC, value)) {
            setUpdated(true);
        }
        this.lockAC = value;
    }

    public @Nullable OnOffType getLockAC() {
        return lockAC;
    }

    public void setSetupPageLock(OnOffType value) {
        if (!Objects.equals(this.setupPageLock, value)) {
            setUpdated(true);
        }
        this.setupPageLock = value;
    }

    public @Nullable OnOffType getSetupPageLock() {
        return setupPageLock;
    }

    public void setLCDBacklightStatus(OnOffType value) {
        if (!Objects.equals(this.lcdBacklightStatus, value)) {
            setUpdated(true);
        }
        this.lcdBacklightStatus = value;
    }

    public @Nullable OnOffType getLCDBacklightStatus() {
        return lcdBacklightStatus;
    }

    public void setBacklight(double value) {
        if (!Objects.equals(this.backlight, value)) {
            setUpdated(true);
        }
        this.backlight = value;
    }

    public @Nullable DecimalType getBacklight() {
        Double value = this.backlight;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    public void setStatusLight(double value) {
        if (!Objects.equals(this.statusLight, value)) {
            setUpdated(true);
        }
        this.statusLight = value;
    }

    public @Nullable DecimalType getStatusLight() {
        Double value = this.statusLight;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }

    private double roundToHalf(double v) {
        return Math.round(v * 2.0) / 2.0;
    }
}
