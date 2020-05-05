/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import org.eclipse.smarthome.core.library.types.PercentType;
import org.openhab.binding.hdl.internal.handler.HdlPacket;

/**
 * The MDT04015 class contains support channels for device Type MDT04015.
 * And how the information on the HDL bus is packet for this device.
 * This is 4 channel, 1.5A dimmer.
 *
 * @author stigla - Initial contribution
 */

public class MDT04015 extends Device {
    // Dimmer 6x1A - Universal
    private PercentType dimChannel1 = null;
    private PercentType dimChannel2 = null;
    private PercentType dimChannel3 = null;
    private PercentType dimChannel4 = null;

    public MDT04015(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Response_Read_Status_of_Channels:
                setDimChannel1(PercentType.valueOf(Integer.toString(p.data[1])));
                setDimChannel2(PercentType.valueOf(Integer.toString(p.data[2])));
                setDimChannel3(PercentType.valueOf(Integer.toString(p.data[3])));
                setDimChannel4(PercentType.valueOf(Integer.toString(p.data[4])));
                break;
            case Response_Read_Current_Level_of_Channels:
                setDimChannel1(PercentType.valueOf(Integer.toString(p.data[1])));
                setDimChannel2(PercentType.valueOf(Integer.toString(p.data[2])));
                setDimChannel3(PercentType.valueOf(Integer.toString(p.data[3])));
                setDimChannel4(PercentType.valueOf(Integer.toString(p.data[4])));
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
                    default:
                        LOGGER.debug("For type: {}, CommandType: {}, does not support channel: {}.", p.sourcedeviceType,
                                p.commandType, p.data[0]);
                        break;
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

    public void setDimChannel1(PercentType DimChannel1) {
        if (this.dimChannel1 != DimChannel1) {
            setUpdated(true);
        }
        this.dimChannel1 = DimChannel1;
    }

    public PercentType getDimChannel1State() {
        return dimChannel1;
    }

    public void setDimChannel2(PercentType DimChannel2) {
        if (this.dimChannel2 != DimChannel2) {
            setUpdated(true);
        }
        this.dimChannel2 = DimChannel2;
    }

    public PercentType getDimChannel2State() {
        return dimChannel2;
    }

    public void setDimChannel3(PercentType DimChannel3) {
        if (this.dimChannel3 != DimChannel3) {
            setUpdated(true);
        }
        this.dimChannel3 = DimChannel3;
    }

    public PercentType getDimChannel3State() {
        return dimChannel3;
    }

    public void setDimChannel4(PercentType DimChannel4) {
        if (this.dimChannel4 != DimChannel4) {
            setUpdated(true);
        }
        this.dimChannel4 = DimChannel4;
    }

    public PercentType getDimChannel4State() {
        return dimChannel4;
    }

    @Override
    public DeviceType getType() {
        return DeviceType.MDT04015_433;
    }
}
