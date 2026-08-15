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
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StopMoveType;

/**
 * The MS08Mn2C class contains support channels for device Type MS08.
 * And how the information on the HDL bus is packet for this device.
 * This is a sensor with 8 functions.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MS08 extends Device {

    private static final int CHANNEL_COUNT = 2;

    private @Nullable Double temperatureValue;
    private @Nullable Double brightnessValue;
    private @Nullable StopMoveType motionSensorValue = null;

    /** Dry contact state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable OpenClosedType[] dryContacts = new OpenClosedType[CHANNEL_COUNT + 1];

    /** Device type for this sensor with 8 functions **/
    private DeviceType deviceType = DeviceType.MS08Mn_2C;

    public MS08(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Broadcast_Sensors_Status_Automatically:
                setTemperatureValue(p.data[0] - 20);
                setBrightnessValue(ushort(p.data[2], p.data[1]));
                setMotionSensorValue(p.data[4] == 1 ? StopMoveType.MOVE : StopMoveType.STOP);
                setDryContactValue(1, p.data[5] == 1 ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                setDryContactValue(2, p.data[6] == 1 ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                break;
            case Response_Read_Sensors_Status:
                if (p.data[0] == -8) {
                    setTemperatureValue(p.data[1] - 20);
                    setBrightnessValue(ushort(p.data[2], p.data[3]));
                    setMotionSensorValue(p.data[4] == 1 ? StopMoveType.MOVE : StopMoveType.STOP);
                    setDryContactValue(1, p.data[5] == 1 ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                    setDryContactValue(2, p.data[6] == 1 ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                }
                break;
            case Response_Auto_broadcast_Dry_Contact_Status:
            case Response_Read_Dry_Contact_Status:
            case Auto_broadcast_Dry_Contact_Status:
                // data[1] holds the 1-based channel number that changed, data[2] its new state.
                int channel = p.data[1];
                if (channel >= 1 && channel <= CHANNEL_COUNT) {
                    setDryContactValue(channel, p.data[2] == 1 ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                }
                break;
            default:
                LOGGER.debug("For Device Type: {}, Unhandled CommandType: {}.", getType(), p.commandType);
                break;
        }
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this sensor.
     *
     * @param DeviceType as provided by the C message
     */
    void setType(DeviceType type) {
        this.deviceType = type;
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
     * Sets the Motion sensor for 8in1 Sensor.
     *
     * @param StopMoveType Value of the Motion sensor
     */
    public void setMotionSensorValue(StopMoveType value) {
        if (this.motionSensorValue != value) {
            setUpdated(true);
        }
        this.motionSensorValue = value;
    }

    /**
     * the Motion sensor Value as <code>StopMoveType</code>
     */
    public @Nullable StopMoveType getMotionSensorValue() {
        return motionSensorValue;
    }

    /**
     * Sets the Brightness Value for this 8in1 Sensor.
     *
     * @param value the Brightness value as provided
     */
    public void setBrightnessValue(double value) {
        if (!Objects.equals(this.brightnessValue, value)) {
            setUpdated(true);
        }
        this.brightnessValue = value;
    }

    /**
     * the BrightnessHighValue as <code>DecimalType</code>, or null if not yet received.
     */
    public @Nullable DecimalType getBrightnessValue() {
        Double value = this.brightnessValue;
        return value != null ? new DecimalType(BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)) : null;
    }

    /**
     * Sets the temperature for this 8in1 sensor.
     *
     * @param value the actual temperature raw value as provided by the L message
     */
    public void setTemperatureValue(double value) {
        if (!Objects.equals(this.temperatureValue, value)) {
            setUpdated(true);
        }
        this.temperatureValue = value;
    }

    /**
     * Returns the measured temperature of this sensor, or null if not yet received.
     *
     * @return
     *         the actual temperature as <code>DecimalType</code>
     */
    public @Nullable DecimalType getTemperatureValue() {
        Double value = this.temperatureValue;
        return value != null ? new DecimalType(BigDecimal.valueOf(value)) : null;
    }
}
