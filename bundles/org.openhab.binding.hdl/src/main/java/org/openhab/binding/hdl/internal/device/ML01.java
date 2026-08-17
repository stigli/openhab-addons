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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.OnOffType;

/**
 * The ML01 class contains support channels for device Type ML01.
 * and how the information on the HDL bus is packet for this device.
 * This is a logic module.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class ML01 extends Device implements UniversalSwitchDevice {

    private @Nullable Date dateTime = null;

    /** Universal switch state, keyed by switch number (see {@link UniversalSwitchDevice}). **/
    private final Map<Integer, OnOffType> uvSwitches = new HashMap<>();

    /** Device type for this Logic Panel **/
    private DeviceType deviceType = DeviceType.ML01;

    public ML01(DeviceConfiguration c) {
        super(c);
    }

    @SuppressWarnings("deprecation")
    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Broadcast_System_Date_and_Time_Every_Minute:
                // p.data[0] is years since 2000; Date's deprecated (year, ...) constructor adds 1900
                // internally, so the year argument must be (years since 2000) + 100, not +2100.
                // p.data[1] is the month as 1-12 (confirmed against real hardware: without the -1, the
                // reported date was consistently one month ahead), but Date's constructor expects 0-11.
                Date aDate = new Date(p.data[0] + 100, p.data[1] - 1, p.data[2], p.data[3], p.data[4], p.data[5]);
                setDateSetpoint(aDate);
                setUpdated(true);
                // LOGGER.debug("Time is: {}", aDate);
                break;
            case Response_UV_Switch_Control:
                setUVSwitch(p.data[0] & 0xff, p.data[1] == 1 ? OnOffType.ON : OnOffType.OFF);
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
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

    public void setDateSetpoint(@Nullable Date date) {
        this.dateTime = date;
    }

    public @Nullable Date getDateSetpoint() {
        return dateTime;
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this Logic Module.
     *
     * @param DeviceType as provided by the hdlPacket
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }
}
