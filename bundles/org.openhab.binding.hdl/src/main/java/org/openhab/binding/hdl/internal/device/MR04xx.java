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

import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.OnOffType;

/**
 * The MR04xx class contains support channels for device Type MR04xx.
 * And how the information on the HDL bus is packet for this device.
 * This is a relay block with 4 relays.
 *
 * @author stigla - Initial contribution
 */

public class MR04xx extends Device {

    /** Device type for this Relay 4x16A **/
    private DeviceType deviceType = DeviceType.MR0416_C;

    private OnOffType relayCh01 = null;
    private OnOffType relayCh02 = null;
    private OnOffType relayCh03 = null;
    private OnOffType relayCh04 = null;

    public MR04xx(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Response_Read_Status_of_Channels:
                if (p.data[1] == 0) {
                    setRelayCh01(OnOffType.OFF);
                } else {
                    setRelayCh01(OnOffType.ON);
                }
                if (p.data[2] == 0) {
                    setRelayCh02(OnOffType.OFF);
                } else {
                    setRelayCh02(OnOffType.ON);
                }
                if (p.data[3] == 0) {
                    setRelayCh03(OnOffType.OFF);
                } else {
                    setRelayCh03(OnOffType.ON);
                }
                if (p.data[4] == 0) {
                    setRelayCh04(OnOffType.OFF);
                } else {
                    setRelayCh04(OnOffType.ON);
                }
                break;
            case Response_Read_Current_Level_of_Channels:
                if (p.data[1] == 0) {
                    setRelayCh01(OnOffType.OFF);
                } else {
                    setRelayCh01(OnOffType.ON);
                }
                if (p.data[2] == 0) {
                    setRelayCh02(OnOffType.OFF);
                } else {
                    setRelayCh02(OnOffType.ON);
                }
                if (p.data[3] == 0) {
                    setRelayCh03(OnOffType.OFF);
                } else {
                    setRelayCh03(OnOffType.ON);
                }
                if (p.data[4] == 0) {
                    setRelayCh04(OnOffType.OFF);
                } else {
                    setRelayCh04(OnOffType.ON);
                }
                break;
            case Response_Single_Channel_Control:
                if ((p.data[4] & 0x01) == 1) {
                    setRelayCh01(OnOffType.ON);
                } else {
                    setRelayCh01(OnOffType.OFF);
                }
                if ((p.data[4] & 0x02) == 2) {
                    setRelayCh02(OnOffType.ON);
                } else {
                    setRelayCh02(OnOffType.OFF);
                }
                if ((p.data[4] & 0x04) == 4) {
                    setRelayCh03(OnOffType.ON);
                } else {
                    setRelayCh03(OnOffType.OFF);
                }
                if ((p.data[4] & 0x08) == 8) {
                    setRelayCh04(OnOffType.ON);
                } else {
                    setRelayCh04(OnOffType.OFF);
                }

                break;
            case Broadcast_Status_of_Scene:
                LOGGER.debug("For type: {}, CommandType: {} Needs a lot of work.", p.sourcedeviceType, p.commandType);
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
    }

    public void setRelayCh01(OnOffType RelayCh01) {
        if (this.relayCh01 != RelayCh01) {
            setUpdated(true);
        }
        this.relayCh01 = RelayCh01;
    }

    public OnOffType getRelayCh01State() {
        return relayCh01;
    }

    public void setRelayCh02(OnOffType RelayCh02) {
        if (this.relayCh02 != RelayCh02) {
            setUpdated(true);
        }
        this.relayCh02 = RelayCh02;
    }

    public OnOffType getRelayCh02State() {
        return relayCh02;
    }

    public void setRelayCh03(OnOffType RelayCh03) {
        if (this.relayCh03 != RelayCh03) {
            setUpdated(true);
        }
        this.relayCh03 = RelayCh03;
    }

    public OnOffType getRelayCh03State() {
        return relayCh03;
    }

    public void setRelayCh04(OnOffType RelayCh04) {
        if (this.relayCh04 != RelayCh04) {
            setUpdated(true);
        }
        this.relayCh04 = RelayCh04;
    }

    public OnOffType getRelayCh04State() {
        return relayCh04;
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
