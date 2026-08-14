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
import java.math.RoundingMode;
import java.util.Date;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StopMoveType;

/**
 * The MS122C class contains support channels for device Type MS12.
 * And how the information on the HDL bus is packet for this device.
 * This is a sensor with 12 functions.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MS12 extends Device {

    private static final int CHANNEL_COUNT = 2;

    private double temperatureValue;
    private double brightnessValue;
    private @Nullable StopMoveType motionSensorValue = null;
    private @Nullable StopMoveType sonicValue = null;

    /** Dry contact state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable OpenClosedType[] dryContacts = new OpenClosedType[CHANNEL_COUNT + 1];

    /** Relay state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable OnOffType[] relayChannels = new OnOffType[CHANNEL_COUNT + 1];

    /** Date setpoint until the temperature setpoint is valid */
    private @Nullable Date dateSetpoint;

    /** Device type for this Sensor with 12 functions **/
    private DeviceType deviceType = DeviceType.MS12_2C;

    public MS12(DeviceConfiguration c) {
        super(c);
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this thermostat.
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }

    public void setDateSetpoint(Date date) {
        this.dateSetpoint = date;
    }

    public @Nullable Date getDateSetpoint() {
        return dateSetpoint;
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Broadcast_Sensors_Status_Automatically:
                setTemperatureValue(p.data[0] - 20.0);
                setBrightnessValue(ushort(p.data[2], p.data[1]));
                setMotionSensorValue(p.data[3] == 1 ? StopMoveType.MOVE : StopMoveType.STOP);
                setSonicValue(p.data[4] == 1 ? StopMoveType.MOVE : StopMoveType.STOP);
                setDryContactValue(1, p.data[5] == 1 ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                setDryContactValue(2, p.data[6] == 1 ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                break;
            case Response_Read_Sensors_Status:
                if (p.data[0] == -8) {
                    setTemperatureValue(p.data[1] - 20.0);
                    setBrightnessValue(ushort(p.data[2], p.data[3]));
                    setMotionSensorValue(p.data[4] == 1 ? StopMoveType.MOVE : StopMoveType.STOP);
                    setSonicValue(p.data[5] == 1 ? StopMoveType.MOVE : StopMoveType.STOP);
                    setDryContactValue(1, p.data[6] == 1 ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                    setDryContactValue(2, p.data[7] == 1 ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                }
                break;
            case Broadcast_Temperature:
                String intoHex = String.format("%02X", p.data[5]) + String.format("%02X", p.data[4])
                        + String.format("%02X", p.data[3]) + String.format("%02X", p.data[2]);

                Long i = Long.valueOf(intoHex, 16);
                Float tempfloat = Float.intBitsToFloat(i.intValue());
                setTemperatureValue(tempfloat);
                break;
            case Response_Single_Channel_Control:
                // data[0] holds the 1-based channel number, data[2] its new state (100 = ON).
                int relayChannel = p.data[0];
                if (relayChannel >= 1 && relayChannel <= CHANNEL_COUNT) {
                    setRelayChannel(relayChannel, p.data[2] == 100 ? OnOffType.ON : OnOffType.OFF);
                }
                break;
            case Response_Auto_broadcast_Dry_Contact_Status:
            case Response_Read_Dry_Contact_Status:
            case Auto_broadcast_Dry_Contact_Status:
                // data[1] holds the 1-based channel number that changed, data[2] its new state.
                int contactChannel = p.data[1];
                if (contactChannel >= 1 && contactChannel <= CHANNEL_COUNT) {
                    setDryContactValue(contactChannel, p.data[2] == 1 ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                }
                break;
            default:
                LOGGER.debug("For Device Type: {}, Unhandled CommandType: {}.", getType(), p.commandType);
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

    /**
     * Sets the Sonic sensor for 12in1 Sensor.
     *
     * @param OnOff Value of the Sonic sensor
     */
    public void setSonicValue(StopMoveType value) {
        if (this.sonicValue != value) {
            setUpdated(true);
        }
        this.sonicValue = value;
    }

    /**
     * the Sonic sensor Value as <code>OnOffType</code>
     */
    public @Nullable StopMoveType getSonicValue() {
        return sonicValue;
    }

    /**
     * Sets the Motion sensor for 12in1 Sensor.
     *
     * @param OnOff Value of the Motion sensor
     */
    public void setMotionSensorValue(StopMoveType value) {
        if (this.motionSensorValue != value) {
            setUpdated(true);
        }
        this.motionSensorValue = value;
    }

    /**
     * the Motion sensor Value as <code>OnOffType</code>
     */
    public @Nullable StopMoveType getMotionSensorValue() {
        return motionSensorValue;
    }

    /**
     * Sets the Brightness Value for this Sensor.
     *
     * @param value the Brightness value as provided
     */
    public void setBrightnessValue(double value) {
        if (this.brightnessValue != value) {
            setUpdated(true);
        }
        this.brightnessValue = value;
    }

    /**
     * the Brightness Value as <code>DecimalType</code>
     */

    public DecimalType getBrightnessValue() {
        BigDecimal brightnessValue = BigDecimal.valueOf(this.brightnessValue).setScale(1, RoundingMode.HALF_UP);
        return new DecimalType(brightnessValue);
    }

    /**
     * Sets the actual temperature for this 12in1 sensor.
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
}
