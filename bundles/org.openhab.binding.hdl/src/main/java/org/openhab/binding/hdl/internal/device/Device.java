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

import java.util.HashMap;
import java.util.List;

import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The device class defines all the support HDL Device that is
 * used across the whole binding.
 *
 * @author stigla - Initial contribution
 */

public abstract class Device {
    protected static final Logger LOGGER = LoggerFactory.getLogger(Device.class);

    private int subNet = -1;
    private String serialNr = "";
    private int deviceID = -1;
    private boolean updated;

    private HashMap<String, Object> properties = new HashMap<>();

    public Device(DeviceConfiguration c) {
        this.subNet = c.getsubNet();
        this.deviceID = c.getdeviceID();
        this.serialNr = c.getSerialNr();
        this.setProperties(new HashMap<>(c.getProperties()));
    }

    public abstract DeviceType getType();

    public static Device create(String serialNr, List<DeviceConfiguration> configurations) {
        if (serialNr == null || configurations == null) {
            LOGGER.debug("Device.create called with null serial or configurations (serial={}, configs={})", serialNr,
                    configurations == null ? 0 : configurations.size());
            return null;
        }
        String incoming = serialNr.trim();
        for (DeviceConfiguration c : configurations) {
            String cSerial = c == null ? null : c.getSerialNr();
            LOGGER.debug("Comparing incoming serial '{}' with config serial '{}'", incoming, cSerial);
            if (cSerial != null && cSerial.trim().equalsIgnoreCase(incoming)) {
                LOGGER.debug("Match found for serial '{}'", incoming);
                return create(c);
            }
        }
        LOGGER.debug("No matching configuration found for serial '{}'", incoming);
        return null;
    }

    public static Device create(HdlPacket p, List<DeviceConfiguration> configurations) {
        Device device = Device.create(p.serialNr, configurations);
        if (device == null) {
            LOGGER.warn("Can't create device from received message, returning NULL.");
            return null;
        }
        return Device.update(p, configurations, device);
    }

    public static Device create(DeviceConfiguration c) {
        switch (c.getDeviceType()) {
            case MDT0601_233:
                return new MDT0601(c);
            case MDT04015_433:
                return new MDT04015(c);
            case ML01:
                return new ML01(c);
            case MPL8_48_FH:
                return new MPL848FH(c);
            case MFH06_432:
                return new MFH06(c);
            case MPT04_48:
                return new MPT0448(c);
            case MR1610_433:
                return new MR16xx(c);
            case MR1216_233:
            case MR1210_433:
                return new MR12xx(c);
            case MR0810_432:
                return new MR08xx(c);
            case MR0416_C:
            case MR0416_231:
            case MR0416_431:
            case MR0410_431:
                return new MR04xx(c);
            case MRDA06:
                return new MRDA06(c);
            case MSP08M_4C:
            case MS08Mn_2C:
                return new MS08(c);
            case MS12_2C:
                return new MS12(c);
            case MS24:
                return new MS24(c);
            case MW02_231:
                return new MW02(c);
            default:
                LOGGER.debug("In HDLPacket Type: {} but unhandled device.", c.getDeviceType());
                return new UnsupportedDevice(c);
        }
    }

    public static Device update(HdlPacket p, List<DeviceConfiguration> configurations, Device device) {
        switch (device.getType()) {
            case MDT0601_233:
                MDT0601 mdt0601233 = (MDT0601) device;
                mdt0601233.treatHDLPacketForDevice(p);
                break;
            case MDT04015_433:
                MDT04015 mdt04015 = (MDT04015) device;
                mdt04015.treatHDLPacketForDevice(p);
                break;
            case ML01:
                ML01 ml01 = (ML01) device;
                ml01.treatHDLPacketForDevice(p);
                break;
            case MPL8_48_FH:
                MPL848FH mpl848fh = (MPL848FH) device;
                mpl848fh.treatHDLPacketForDevice(p);
                break;
            case MFH06_432:
                MFH06 mfh06 = (MFH06) device;
                mfh06.treatHDLPacketForDevice(p);
                break;
            case MPT04_48:
                MPT0448 mpt0448 = (MPT0448) device;
                mpt0448.treatHDLPacketForDevice(p);
                break;
            case MR1610_433:
                MR16xx mr16xx = (MR16xx) device;
                mr16xx.treatHDLPacketForDevice(p);
                break;
            case MR1216_233:
            case MR1210_433:
                MR12xx mr12xx = (MR12xx) device;
                mr12xx.treatHDLPacketForDevice(p);
                break;
            case MR0816_432:
            case MR0810_432:
                MR08xx mr08xx = (MR08xx) device;
                mr08xx.treatHDLPacketForDevice(p);
                break;
            case MR0416_C:
            case MR0416_231:
            case MR0416_431:
            case MR0410_431:
                MR04xx mr0416 = (MR04xx) device;
                mr0416.treatHDLPacketForDevice(p);
                break;
            case MRDA06:
                MRDA06 mrda06 = (MRDA06) device;
                mrda06.treatHDLPacketForDevice(p);
                break;
            case MSP08M_4C:
            case MS08Mn_2C:
                MS08 ms08mn2c = (MS08) device;
                ms08mn2c.treatHDLPacketForDevice(p);
                break;
            case MS12_2C:
                MS12 ms122c = (MS12) device;
                ms122c.treatHDLPacketForDevice(p);
                break;
            case MS24:
                MS24 ms24 = (MS24) device;
                ms24.treatHDLPacketForDevice(p);
                break;
            case MW02:
                MW02 mw02 = (MW02) device;
                mw02.treatHDLPacketForDevice(p);
                break;
            default:
                LOGGER.debug("In HDLPacket Type: {} but unhandled device: {} in Device.", p.sourcedeviceType,
                        device.getType());
                break;
        }
        return device;
    }

    protected static int ushort(byte h, byte l) {
        return ((h << 8) & 0xff00) | (l & 0xff);
    }

    public String getSerialNr() {
        return this.serialNr;
    }

    public void setSerialNr(String SerialNr) {
        this.serialNr = SerialNr;
    }

    public final int getsubNet() {
        return subNet;
    }

    public final void setsubNet(int subNet) {
        this.subNet = subNet;
    }

    public final int getdeviceID() {
        return deviceID;
    }

    public final void setdeviceID(int deviceID) {
        this.deviceID = deviceID;
    }

    public boolean isUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    /**
     * @return the properties
     */
    public HashMap<String, Object> getProperties() {
        return properties;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(HashMap<String, Object> properties) {
        this.properties = new HashMap<>(properties);
    }

    @Override
    public String toString() {
        return this.getType().toString() + this.getSerialNr();
    }
}
