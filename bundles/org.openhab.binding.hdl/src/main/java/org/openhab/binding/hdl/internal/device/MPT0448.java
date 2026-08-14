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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.library.types.OnOffType;

/**
 * The MPT04_48 class contains support channels for device Type MPT04.
 * And how the information on the HDL bus is packet for this device.
 * This is a button panel with 4 buttons.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class MPT0448 extends Device {

    private static final int CHANNEL_COUNT = 4;

    /** Device type for Digital touch switch 4 buttons **/
    private DeviceType deviceType = DeviceType.MPT04_48;

    /** Button state per channel; 1-indexed to match the HDL protocol, index 0 is unused. **/
    private final @Nullable OnOffType[] buttons = new OnOffType[CHANNEL_COUNT + 1];

    /**
     * Set when a button is confirmed (on real hardware) to have been pressed, via one of the
     * command types handled below, other than the Panel Control family itself: this must stay false for
     * Panel_Control/Response_Panel_Control/Response_Read_Status_of_Panel_Control, since those are also the
     * reply to our own status probe, and reacting to them here would cause an endless probe-reply-probe loop.
     * Consumed (and cleared) via {@link #consumeControlEvent()}.
     **/
    private boolean controlEventPending;

    public MPT0448(DeviceConfiguration c) {
        super(c);
    }

    public void treatHDLPacketForDevice(HdlPacket p) {
        switch (p.commandType) {
            case Panel_Control:
            case Response_Panel_Control:
            case Response_Read_Status_of_Panel_Control:
                // Best-effort mapping, not yet confirmed against real MPT04.48 traffic: data[0] is assumed
                // to be the 1-based button number and data[1] its new state (non-zero = pressed/on), matching
                // the {key, value} wire format the Panel Control command family uses for MPL848FH's panel keys.
                int key = p.data[0];
                if (key >= 1 && key <= CHANNEL_COUNT) {
                    setButtonValue(key, p.data[1] != 0 ? OnOffType.ON : OnOffType.OFF);
                }
                break;
            case Single_Channel_Control:
            case Scene_Control:
            case Universal_control:
                // Confirmed on real hardware: this panel's buttons are programmed (via the HDL Buspro Setup
                // Tool) to directly drive another device instead of reporting their own state, so we can't
                // tell which button was pressed or its new value from these packets. They do confirm a
                // button was just pressed though, so flag it for the handler to request a fresh status read.
                controlEventPending = true;
                setUpdated(true);
                break;
            default:
                LOGGER.debug("For type: {}, Unhandled CommandType: {}.", p.sourcedeviceType, p.commandType);
                break;
        }
    }

    /**
     * Returns whether a control event (button press routed to another device, see
     * {@link #treatHDLPacketForDevice}) has been seen since the last call, and clears the flag.
     */
    public boolean consumeControlEvent() {
        boolean pending = controlEventPending;
        controlEventPending = false;
        return pending;
    }

    @Override
    public DeviceType getType() {
        return deviceType;
    }

    /**
     * Sets the DeviceType for this touch panel.
     *
     *
     */
    void setType(DeviceType type) {
        this.deviceType = type;
    }

    private void setButtonValue(int channel, OnOffType value) {
        if (buttons[channel] != value) {
            setUpdated(true);
        }
        buttons[channel] = value;
    }

    private @Nullable OnOffType getButtonValue(int channel) {
        return buttons[channel];
    }

    /**
     * Sets the ButtonValue value for touch panel.
     *
     * @param OnOff Value of the Button1
     */
    public void setbutton1Value(OnOffType value) {
        setButtonValue(1, value);
    }

    /**
     * the button1 Value as <code>OnOffType</code>
     */
    public @Nullable OnOffType getbutton1Value() {
        return getButtonValue(1);
    }

    /**
     * Sets the ButtonValue value for touch panel.
     *
     * @param OnOff Value of the Button2
     */
    public void setbutton2Value(OnOffType value) {
        setButtonValue(2, value);
    }

    /**
     * the button2 Value as <code>OnOffType</code>
     */
    public @Nullable OnOffType getbutton2Value() {
        return getButtonValue(2);
    }

    /**
     * Sets the ButtonValue value for touch panel.
     *
     * @param OnOff Value of the Button3
     */
    public void setbutton3Value(OnOffType value) {
        setButtonValue(3, value);
    }

    /**
     * the button2 Value as <code>OnOffType</code>
     */
    public @Nullable OnOffType getbutton3Value() {
        return getButtonValue(3);
    }

    /**
     * Sets the ButtonValue value for touch panel.
     *
     * @param OnOff Value of the Button4
     */
    public void setbutton4Value(OnOffType value) {
        setButtonValue(4, value);
    }

    /**
     * the button2 Value as <code>OnOffType</code>
     */
    public @Nullable OnOffType getbutton4Value() {
        return getButtonValue(4);
    }
}
