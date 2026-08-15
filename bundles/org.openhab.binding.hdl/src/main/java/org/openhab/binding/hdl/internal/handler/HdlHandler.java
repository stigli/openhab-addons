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
package org.openhab.binding.hdl.internal.handler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hdl.HdlBindingConstants;
import org.openhab.binding.hdl.internal.device.CommandType;
import org.openhab.binding.hdl.internal.device.Device;
import org.openhab.binding.hdl.internal.device.MDT04015;
import org.openhab.binding.hdl.internal.device.MDT0601;
import org.openhab.binding.hdl.internal.device.MFH06;
import org.openhab.binding.hdl.internal.device.ML01;
import org.openhab.binding.hdl.internal.device.MPL848FH;
import org.openhab.binding.hdl.internal.device.MPT0448;
import org.openhab.binding.hdl.internal.device.MR04xx;
import org.openhab.binding.hdl.internal.device.MR08xx;
import org.openhab.binding.hdl.internal.device.MR12xx;
import org.openhab.binding.hdl.internal.device.MR16xx;
import org.openhab.binding.hdl.internal.device.MRDA06;
import org.openhab.binding.hdl.internal.device.MS08;
import org.openhab.binding.hdl.internal.device.MS12;
import org.openhab.binding.hdl.internal.device.MS24;
import org.openhab.binding.hdl.internal.device.MW02;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdlHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 * Inspired by MAX! binding,
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class HdlHandler extends BaseThingHandler implements DeviceStatusListener {

    private Logger logger = LoggerFactory.getLogger(HdlHandler.class);
    private @Nullable HdlBridgeHandler bridgeHandler;

    private @Nullable String hdldeviceSerial;
    private int subNet;
    private int deviceID;
    private int refreshRate;
    private int channelNumber;
    private @Nullable ScheduledFuture<?> refreshJob;

    public HdlHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        try {
            Configuration config = getThing().getConfiguration();
            subNet = ((BigDecimal) config.get(HdlBindingConstants.PROPERTY_SUBNET)).intValueExact();
            deviceID = ((BigDecimal) config.get(HdlBindingConstants.PROPERTY_DEVICEID)).intValueExact();

            try {
                refreshRate = ((BigDecimal) config.get(HdlBindingConstants.PROPERTY_REFRESHRATE)).intValueExact();
            } catch (Exception e) {
                refreshRate = 0;
            }

            try {
                channelNumber = ((BigDecimal) config.get(HdlBindingConstants.PROPERTY_CHANNELNUMBER)).intValueExact();
            } catch (Exception e) {
                channelNumber = 0;
            }

            if (channelNumber != 0) {
                hdldeviceSerial = Integer.toString(subNet * 1000 + deviceID) + "_" + Integer.toString(channelNumber);
            } else {
                hdldeviceSerial = Integer.toString(subNet * 1000 + deviceID);
            }

            if (hdldeviceSerial != null) {
                logger.debug("Initialized Hdl! device handler for {}.", hdldeviceSerial);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Initialized HDL device missing Subnet or DeviceID configuration");
            }

            @Nullable
            HdlBridgeHandler hdlBridge = getHdlBridgeHandler();

            if (hdlBridge != null && getThing().getStatus().equals(ThingStatus.ONLINE)) {
                sendUpdatePackets(hdlBridge);
                // refreshRate == -1 means event-only refresh (currently only used by hdl:MPT04): no fixed-delay
                // job is scheduled, status is instead requested reactively, see onDeviceStateChanged.
                if (refreshRate > 0) {
                    if (refreshJob == null || refreshJob.isCancelled()) {
                        refreshJob = scheduler.scheduleWithFixedDelay(refreshRunnable, 1, refreshRate,
                                TimeUnit.SECONDS);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Exception occurred during initialize : {}", e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.smarthome.core.thing.binding.BaseThingHandler#dispose()
     */
    @Override
    public void dispose() {
        logger.debug("Disposing Hdl! device {} {}.", getThing().getUID(), hdldeviceSerial);

        if (bridgeHandler != null) {
            logger.trace("Clear HDL! device {} {} from bridge.", getThing().getUID(), hdldeviceSerial);
            bridgeHandler.clearDeviceList();
            bridgeHandler.unregisterDeviceStatusListener(this);
            bridgeHandler = null;
        }

        if (refreshJob != null && !refreshJob.isCancelled()) {
            refreshJob.cancel(true);
            refreshJob = null;
        }

        logger.debug("Disposed HDL! device {} {}.", getThing().getUID(), hdldeviceSerial);
        super.dispose();
    }

    private Runnable refreshRunnable = () -> {
        try {
            HdlPacket p = new HdlPacket();
            p.setTargetSubnetID(subNet);
            p.setTargetDeviceId(deviceID);

            switch (getThing().getThingTypeUID().getAsString()) {
                case "hdl:MSP08M_4C":
                case "hdl:MS08Mn_2C":
                case "hdl:MS08":
                case "hdl:MS12":
                case "hdl:MS12_2C":
                    p.setCommandType(CommandType.Read_Sensors_Status);
                    logger.debug("For Thing Type: {} with device id: {} with Refresh Interval: {} command is sent.",
                            getThing().getThingTypeUID().getAsString(), deviceID, refreshRate);
                    break;
                case "hdl:MPL8_48_FH":
                    p.setCommandType(CommandType.Read_Floor_Heating_Status_DLP);
                    logger.debug("For Thing Type: {} with device id: {} with Refresh Interval: {} command is sent.",
                            getThing().getThingTypeUID().getAsString(), deviceID, refreshRate);
                    break;
                case "hdl:MRDA06":
                case "hdl:MDT0601":
                case "hdl:MDT04015":
                case "hdl:MR16xx":
                case "hdl:MR12xx":
                case "hdl:MR08xx":
                case "hdl:MR04xx":
                    p.setCommandType(CommandType.Read_Status_of_Channels);
                    logger.debug("For Thing Type: {} with device id: {} with Refresh Interval: {} command is sent.",
                            getThing().getThingTypeUID().getAsString(), deviceID, refreshRate);
                    break;
                case "hdl:MFH06":
                    if (channelNumber != 0) {
                        p.setCommandType(CommandType.Read_Floor_Heating_Status);
                        p.setData(new byte[] { (byte) channelNumber });
                        logger.debug("For Thing Type: {} with device id: {} with Refresh Interval: {} command is sent.",
                                getThing().getThingTypeUID().getAsString(), deviceID, refreshRate);
                    } else {
                        logger.debug(
                                "For Thing Type: {} with device id: {}, Refresh not sent since channel number is 0.",
                                getThing().getThingTypeUID().getAsString(), deviceID);
                    }
                    break;
                case "hdl:MPT04": {
                    // Needs one request per button (key 1-4), so send them directly here instead of
                    // relying on the single-packet send below.
                    var bridge = bridgeHandler;
                    if (bridge != null) {
                        sendMpt04StatusProbe(bridge);
                    }
                    logger.debug("For Thing Type: {} with device id: {} with Refresh Interval: {} command is sent.",
                            getThing().getThingTypeUID().getAsString(), deviceID, refreshRate);
                    return;
                }

                /*
                 * case "hdl:MRDA06":
                 * case "hdl:MDT0601_233":
                 * case "hdl:MR1216_233":
                 * p.setCommandType(CommandType.Read_Status_of_Channels);
                 * logger.debug("For Thing Type: {} command: Refresh is sent.",
                 * getThing().getThingTypeUID().getAsString());
                 * break;
                 */
                default:
                    logger.debug("For Thing Type: {} command: Refresh interval is not supported.",
                            getThing().getThingTypeUID().getAsString());
                    refreshJob.cancel(true);
                    return;
            }

            try {
                bridgeHandler.sendPacket(p);
            } catch (IOException e) {
                logger.warn("Could not send msg to bridge, got error msg: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.debug("An exception occurred while refreshing the hdl item: '{}'", e.getMessage());
            updateStatus(ThingStatus.OFFLINE);
        }
    };

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.smarthome.core.thing.binding.BaseThingHandler#thingUpdated
     * (org.eclipse.smarthome.core.thing.Thing)
     */
    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);
    }

    /*
     *
     * @param configurationParameter
     */

    private synchronized @Nullable HdlBridgeHandler getHdlBridgeHandler() {
        if (this.bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                logger.debug("Required bridge not defined for device {}.", hdldeviceSerial);
                updateStatus(ThingStatus.OFFLINE);
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof HdlBridgeHandler) {
                this.bridgeHandler = (HdlBridgeHandler) handler;
                this.bridgeHandler.registerDeviceStatusListener(this);
                // Pre-seed this already-configured device as "known" so its first packet after a bridge
                // restart doesn't spuriously trigger onDeviceAdded()/an "Adding new Hdl!" discovery log -
                // lastActiveDevices gets cleared on every handler dispose (see unregisterDeviceStatusListener),
                // and nothing else was ever repopulating it for devices that already have a configured Thing.
                String serial = hdldeviceSerial;
                if (serial != null) {
                    this.bridgeHandler.addTolastActiveDeviceList(serial);
                }
                updateStatus(ThingStatus.ONLINE);
            } else {
                logger.debug("No available bridge handler found for {} bridge {} .", hdldeviceSerial, bridge.getUID());
                updateStatus(ThingStatus.OFFLINE);
                return null;
            }
        }
        return this.bridgeHandler;
    }

    private void sendUpdatePackets(HdlBridgeHandler hdlBridge) {
        Collection<HdlPacket> hdlPacketList = new ArrayList<HdlPacket>();

        HdlPacket p = new HdlPacket();
        p.setTargetSubnetID(subNet);
        p.setTargetDeviceId(deviceID);

        switch (getThing().getThingTypeUID().getAsString()) {
            case "hdl:MSP08M_4C":
            case "hdl:MS08Mn_2C":
            case "hdl:MS08":
            case "hdl:MS12_2C":
            case "hdl:MS12":
                p.setCommandType(CommandType.Read_Sensors_Status);
                hdlPacketList.add(p);
                logger.debug("For Thing Type: {} command: Refresh is sent.",
                        getThing().getThingTypeUID().getAsString());
                break;
            case "hdl:MRDA06":
            case "hdl:MDT0601_233":
            case "hdl:MDT0601":
            case "hdl:MDT04015":
            case "hdl:MR1216_233":
            case "hdl:MR1210_433":
            case "hdl:MR1216":
            case "hdl:MR0416":
            case "hdl:MR04xx":
            case "hdl:MR08xx":
            case "hdl:MR12xx":
            case "hdl:MR16xx":
                p.setCommandType(CommandType.Read_Status_of_Channels);
                hdlPacketList.add(p);
                logger.debug("For Thing Type: {} command: Refresh is sent.",
                        getThing().getThingTypeUID().getAsString());
                break;
            case "hdl:MPL8_48_FH":
                p.setCommandType(CommandType.Read_Floor_Heating_Status_DLP);
                hdlPacketList.add(p);
                logger.debug("For Thing Type: {} command: Refresh is sent.",
                        getThing().getThingTypeUID().getAsString());
                break;
            case "hdl:MFH06":
                if (channelNumber != 0) {
                    p.setCommandType(CommandType.Read_Floor_Heating_Status);
                    hdlPacketList.add(p);
                    p.setData(new byte[] { (byte) channelNumber });
                    logger.debug("For Thing Type: {} command: Refresh is sent.",
                            getThing().getThingTypeUID().getAsString());
                }
                break;
            case "hdl:MW02_231":
            case "hdl:MW02":
                p.setCommandType(CommandType.Read_Status_of_Curtain_Switch);
                p.setData(new byte[] { (byte) 1 });
                hdlPacketList.add(p);

                HdlPacket p2 = new HdlPacket();
                p2.setTargetSubnetID(subNet);
                p2.setTargetDeviceId(deviceID);
                p2.setCommandType(CommandType.Read_Status_of_Curtain_Switch);
                p2.setData(new byte[] { (byte) 2 });
                hdlPacketList.add(p2);

                logger.debug("For Thing Type: {} command: Refresh is sent.",
                        getThing().getThingTypeUID().getAsString());
                break;
            case "hdl:MPT04":
                // One request per button (key 1-4); see MPT0448's Panel Control handling for the same
                // not-yet-verified key/value assumption this relies on.
                hdlPacketList.addAll(buildMpt04StatusProbePackets());
                logger.debug("For Thing Type: {} command: Refresh is sent.",
                        getThing().getThingTypeUID().getAsString());
                break;
            default:
                logger.debug("For Thing Type: {} command: Refresh not supported.",
                        getThing().getThingTypeUID().getAsString());
                return;
        }

        try {
            for (Iterator<HdlPacket> i = hdlPacketList.iterator(); i.hasNext();) {
                HdlPacket item = i.next();
                hdlBridge.sendPacket(item);
            }
        } catch (IOException e) {
            logger.warn("Could not send msg to bridge, got error msg: {}", e.getMessage());
        }
    }

    /**
     * Builds one {@link CommandType#Read_Status_of_Panel_Control} request per button (key 1-4) for a
     * hdl:MPT04 thing, targeting this handler's configured subnet/device.
     */
    private Collection<HdlPacket> buildMpt04StatusProbePackets() {
        Collection<HdlPacket> packets = new ArrayList<HdlPacket>();
        for (byte key = 1; key <= 4; key++) {
            HdlPacket buttonRequest = new HdlPacket();
            buttonRequest.setTargetSubnetID(subNet);
            buttonRequest.setTargetDeviceId(deviceID);
            buttonRequest.setCommandType(CommandType.Read_Status_of_Panel_Control);
            buttonRequest.setData(new byte[] { key });
            packets.add(buttonRequest);
        }
        return packets;
    }

    /**
     * Sends a status probe (see {@link #buildMpt04StatusProbePackets()}) for a hdl:MPT04 thing, used both
     * by the fixed-delay {@link #refreshRunnable} and by the event-only ({@code refreshRate == -1}) reactive
     * trigger in {@link #onDeviceStateChanged}.
     */
    private void sendMpt04StatusProbe(HdlBridgeHandler hdlBridge) {
        for (HdlPacket packet : buildMpt04StatusProbePackets()) {
            try {
                hdlBridge.sendPacket(packet);
            } catch (IOException e) {
                logger.warn("Could not send msg to bridge, got error msg: {}", e.getMessage());
            }
        }
    }

    /**
     * Maps a FHMode command/state string ("Normal"/"Day"/"Night"/"Away"/"Timer", case-insensitive) to the
     * wire value used by {@link CommandType#Control_Floor_Heating_Status_DLP}.
     */
    private static int floorHeatingModeNameToInt(String modeName) {
        switch (modeName.toLowerCase()) {
            case "normal":
                return 1;
            case "day":
                return 2;
            case "night":
                return 3;
            case "away":
                return 4;
            case "timer":
                return 5;
            default:
                return 0;
        }
    }

    /**
     * Builds a {@link CommandType#Control_Floor_Heating_Status_DLP} packet, reusing the device's cached
     * temperature-unit and on/off status (only the mode and the four setpoints are ever commanded from
     * openHAB). Returns null if the device hasn't reported real status yet, since sending this command
     * with a default/unknown temperature unit or on/off status could write bad state to real hardware.
     */
    private @Nullable HdlPacket buildFloorHeatingControlDlpPacket(MPL848FH device, int modenr, int normalTemp,
            int dayTemp, int nightTemp, int awayTemp) {
        String tempType = device.getFloorHeatingTemperaturType();
        OnOffType status = device.getFloorHeatingStatus();
        if (tempType == null || status == null) {
            return null;
        }
        int tempTypenr = "C".equals(tempType) ? 0 : 1;
        int statusNr = status == OnOffType.OFF ? 0 : 1;
        HdlPacket packet = new HdlPacket();
        packet.setData(new byte[] { (byte) tempTypenr, (byte) statusNr, (byte) modenr, (byte) normalTemp,
                (byte) dayTemp, (byte) nightTemp, (byte) awayTemp });
        packet.setCommandType(CommandType.Control_Floor_Heating_Status_DLP);
        return packet;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        HdlBridgeHandler hdlBridge = getHdlBridgeHandler();
        boolean sendCommand = false;
        if (hdlBridge == null) {
            logger.warn("HDL bridge handler not found. Cannot handle command without bridge.");
            return;
        }
        String serial = hdldeviceSerial;
        if (serial == null) {
            logger.warn("Serial number missing. Can't send command to device '{}'", getThing());
            return;
        }

        HdlPacket p = new HdlPacket();

        @Nullable
        Device chDevice = hdlBridge.getDevice(serial);

        if (command instanceof RefreshType) {
            sendUpdatePackets(hdlBridge);
        } else {
            switch (channelUID.getId()) {
                case HdlBindingConstants.CHANNEL_FHMODE:
                    if (getThing().getThingTypeUID().getAsString().equals("hdl:MPL8_48_FH")
                            && chDevice instanceof MPL848FH mplDevice) {
                        var normalTemp = mplDevice.getFloorHeatingSetNormalTemperatur();
                        var dayTemp = mplDevice.getFloorHeatingSetDayTemperatur();
                        var nightTemp = mplDevice.getFloorHeatingSetNightTemperatur();
                        var awayTemp = mplDevice.getFloorHeatingSetAwayTemperatur();
                        if (normalTemp != null && dayTemp != null && nightTemp != null && awayTemp != null) {
                            HdlPacket packet = buildFloorHeatingControlDlpPacket(mplDevice,
                                    floorHeatingModeNameToInt(command.toString()), normalTemp.intValue(),
                                    dayTemp.intValue(), nightTemp.intValue(), awayTemp.intValue());
                            if (packet != null) {
                                p = packet;
                                sendCommand = true;
                            }
                        }
                    }
                    break;
                case HdlBindingConstants.CHANNEL_FHNORMALTEMPSET:
                case HdlBindingConstants.CHANNEL_FHDAYTEMPSET:
                case HdlBindingConstants.CHANNEL_FHNIGHTTEMPSET:
                case HdlBindingConstants.CHANNEL_FHAWAYTEMPSET:
                    Integer newSetpoint = null;
                    if (command instanceof QuantityType<?> quantityCommand) {
                        QuantityType<?> celsius = quantityCommand.toUnit(SIUnits.CELSIUS);
                        if (celsius != null) {
                            newSetpoint = celsius.intValue();
                        }
                    } else if (command instanceof DecimalType decimalCommand) {
                        newSetpoint = decimalCommand.intValue();
                    }
                    if (getThing().getThingTypeUID().getAsString().equals("hdl:MPL8_48_FH")
                            && chDevice instanceof MPL848FH mplDevice && newSetpoint != null) {
                        var mode = mplDevice.getFloorHeatingMode();
                        var normalTemp = mplDevice.getFloorHeatingSetNormalTemperatur();
                        var dayTemp = mplDevice.getFloorHeatingSetDayTemperatur();
                        var nightTemp = mplDevice.getFloorHeatingSetNightTemperatur();
                        var awayTemp = mplDevice.getFloorHeatingSetAwayTemperatur();
                        if (mode != null && normalTemp != null && dayTemp != null && nightTemp != null
                                && awayTemp != null) {
                            int normalTempNr = normalTemp.intValue();
                            int dayTempNr = dayTemp.intValue();
                            int nightTempNr = nightTemp.intValue();
                            int awayTempNr = awayTemp.intValue();
                            switch (channelUID.getId()) {
                                case HdlBindingConstants.CHANNEL_FHNORMALTEMPSET:
                                    normalTempNr = newSetpoint.intValue();
                                    break;
                                case HdlBindingConstants.CHANNEL_FHDAYTEMPSET:
                                    dayTempNr = newSetpoint.intValue();
                                    break;
                                case HdlBindingConstants.CHANNEL_FHNIGHTTEMPSET:
                                    nightTempNr = newSetpoint.intValue();
                                    break;
                                case HdlBindingConstants.CHANNEL_FHAWAYTEMPSET:
                                    awayTempNr = newSetpoint.intValue();
                                    break;
                            }
                            HdlPacket packet = buildFloorHeatingControlDlpPacket(mplDevice,
                                    floorHeatingModeNameToInt(mode.toString()), normalTempNr, dayTempNr, nightTempNr,
                                    awayTempNr);
                            if (packet != null) {
                                p = packet;
                                sendCommand = true;
                            }
                        }
                    }
                    break;
                case HdlBindingConstants.CHANNEL_SHUTTER1CONTROL:
                case HdlBindingConstants.CHANNEL_SHUTTER2CONTROL:
                    String stringCommand = command.toString();
                    p.setCommandType(CommandType.Curtain_Switch_Control);
                    int curtainNr = HdlBindingConstants.CurtainNr.valueOf(channelUID.getId()).getValue();
                    switch (stringCommand) {
                        case "Off":
                        case "OFF":
                        case "DOWN":
                            p.setData(new byte[] { (byte) curtainNr, (byte) 1 });
                            sendCommand = true;
                            break;
                        case "ON":
                        case "UP":
                            p.setData(new byte[] { (byte) curtainNr, (byte) 2 });
                            sendCommand = true;
                            break;
                        case "STOP":
                            p.setData(new byte[] { (byte) curtainNr, (byte) 0 });
                            sendCommand = true;
                            break;
                    }

                    break;
                case HdlBindingConstants.CHANNEL_DIMCHANNEL1:
                case HdlBindingConstants.CHANNEL_DIMCHANNEL2:
                case HdlBindingConstants.CHANNEL_DIMCHANNEL3:
                case HdlBindingConstants.CHANNEL_DIMCHANNEL4:
                case HdlBindingConstants.CHANNEL_DIMCHANNEL5:
                case HdlBindingConstants.CHANNEL_DIMCHANNEL6:
                    Integer dimPercent = null;
                    if (command instanceof PercentType percentCommand) {
                        dimPercent = percentCommand.intValue();
                    } else if (command instanceof OnOffType onOffCommand) {
                        dimPercent = onOffCommand == OnOffType.ON ? 100 : 0;
                    }
                    if (dimPercent != null) {
                        p.setCommandType(CommandType.Single_Channel_Control);
                        int channelNr = HdlBindingConstants.DimChannelNr.valueOf(channelUID.getId()).getValue();
                        p.setData(new byte[] { (byte) channelNr, (byte) dimPercent.intValue(), 0, 0 });
                        sendCommand = true;
                    }
                    break;
                case HdlBindingConstants.CHANNEL_RELAYCH1:
                case HdlBindingConstants.CHANNEL_RELAYCH2:
                case HdlBindingConstants.CHANNEL_RELAYCH3:
                case HdlBindingConstants.CHANNEL_RELAYCH4:
                case HdlBindingConstants.CHANNEL_RELAYCH5:
                case HdlBindingConstants.CHANNEL_RELAYCH6:
                case HdlBindingConstants.CHANNEL_RELAYCH7:
                case HdlBindingConstants.CHANNEL_RELAYCH8:
                case HdlBindingConstants.CHANNEL_RELAYCH9:
                case HdlBindingConstants.CHANNEL_RELAYCH10:
                case HdlBindingConstants.CHANNEL_RELAYCH11:
                case HdlBindingConstants.CHANNEL_RELAYCH12:
                case HdlBindingConstants.CHANNEL_RELAYCH13:
                case HdlBindingConstants.CHANNEL_RELAYCH14:
                case HdlBindingConstants.CHANNEL_RELAYCH15:
                case HdlBindingConstants.CHANNEL_RELAYCH16:
                    if (command instanceof OnOffType) {
                        p.setCommandType(CommandType.Single_Channel_Control);
                        int relayValue = 0;
                        if (command.equals(OnOffType.ON)) {
                            relayValue = 100;
                        }
                        int channelNr = HdlBindingConstants.RelayChannelNr.valueOf(channelUID.getId()).getValue();
                        p.setData(new byte[] { (byte) channelNr, (byte) relayValue, 0, 0 });
                        sendCommand = true;
                    }
                    break;
                case HdlBindingConstants.CHANNEL_UVSWITCH1:
                case HdlBindingConstants.CHANNEL_UVSWITCH2:
                case HdlBindingConstants.CHANNEL_UVSWITCH3:
                case HdlBindingConstants.CHANNEL_UVSWITCH4:
                case HdlBindingConstants.CHANNEL_UVSWITCH5:
                case HdlBindingConstants.CHANNEL_UVSWITCH6:
                case HdlBindingConstants.CHANNEL_UVSWITCH200:
                case HdlBindingConstants.CHANNEL_UVSWITCH201:
                case HdlBindingConstants.CHANNEL_UVSWITCH202:
                case HdlBindingConstants.CHANNEL_UVSWITCH203:
                case HdlBindingConstants.CHANNEL_UVSWITCH204:
                case HdlBindingConstants.CHANNEL_UVSWITCH205:
                case HdlBindingConstants.CHANNEL_UVSWITCH206:
                case HdlBindingConstants.CHANNEL_UVSWITCH207:
                case HdlBindingConstants.CHANNEL_UVSWITCH208:
                case HdlBindingConstants.CHANNEL_UVSWITCH209:
                case HdlBindingConstants.CHANNEL_UVSWITCH210:
                case HdlBindingConstants.CHANNEL_UVSWITCH211:
                case HdlBindingConstants.CHANNEL_UVSWITCH212:
                case HdlBindingConstants.CHANNEL_UVSWITCH213:
                case HdlBindingConstants.CHANNEL_UVSWITCH214:
                case HdlBindingConstants.CHANNEL_UVSWITCH215:
                case HdlBindingConstants.CHANNEL_UVSWITCH216:
                case HdlBindingConstants.CHANNEL_UVSWITCH217:
                case HdlBindingConstants.CHANNEL_UVSWITCH218:
                case HdlBindingConstants.CHANNEL_UVSWITCH219:
                case HdlBindingConstants.CHANNEL_UVSWITCH220:
                case HdlBindingConstants.CHANNEL_UVSWITCH221:
                case HdlBindingConstants.CHANNEL_UVSWITCH222:
                case HdlBindingConstants.CHANNEL_UVSWITCH223:
                case HdlBindingConstants.CHANNEL_UVSWITCH224:
                case HdlBindingConstants.CHANNEL_UVSWITCH225:
                case HdlBindingConstants.CHANNEL_UVSWITCH226:
                case HdlBindingConstants.CHANNEL_UVSWITCH227:
                case HdlBindingConstants.CHANNEL_UVSWITCH228:
                case HdlBindingConstants.CHANNEL_UVSWITCH229:
                case HdlBindingConstants.CHANNEL_UVSWITCH230:
                case HdlBindingConstants.CHANNEL_UVSWITCH231:
                case HdlBindingConstants.CHANNEL_UVSWITCH232:
                case HdlBindingConstants.CHANNEL_UVSWITCH233:
                case HdlBindingConstants.CHANNEL_UVSWITCH234:
                case HdlBindingConstants.CHANNEL_UVSWITCH235:
                case HdlBindingConstants.CHANNEL_UVSWITCH236:
                case HdlBindingConstants.CHANNEL_UVSWITCH237:
                case HdlBindingConstants.CHANNEL_UVSWITCH238:
                case HdlBindingConstants.CHANNEL_UVSWITCH239:
                case HdlBindingConstants.CHANNEL_UVSWITCH240:
                    if (command instanceof OnOffType) {
                        p.setCommandType(CommandType.UV_Switch_Control);
                        int uvswitchValue = 0;
                        if (command.equals(OnOffType.ON)) {
                            uvswitchValue = 255;
                        }
                        int uvswitchNr = HdlBindingConstants.UVSwitchNr.valueOf(channelUID.getId()).getValue();
                        p.setData(new byte[] { (byte) uvswitchNr, (byte) uvswitchValue });
                        sendCommand = true;
                    }
                    break;
                default:
                    logger.warn("For Channel: {} Command: {} Not supported.", channelUID, command);
                    return;
            }
        }

        if (sendCommand) {
            p.setTargetSubnetID(subNet);
            p.setTargetDeviceId(deviceID);

            try {
                hdlBridge.sendPacket(p);
            } catch (IOException e) {
                logger.warn("Could not send msg to bridge, got error msg: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onDeviceStateChanged(ThingUID bridge, Device device) {
        if (device.getSerialNr().equals(hdldeviceSerial)) {
            if (device.isUpdated()) {
                logger.debug("Updating states of {} {} id: {}", device.getType(), device.getSerialNr(),
                        getThing().getUID());
                switch (device.getType()) {
                    case MS08Mn_2C:
                    case MSP08M_4C: {
                        var temperatureValue = ((MS08) device).getTemperatureValue();
                        if (temperatureValue != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_TEMPERATUR),
                                    temperatureValue);
                        }
                    } {
                        var brightnessValue = ((MS08) device).getBrightnessValue();
                        if (brightnessValue != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_BRIGHTNESS),
                                    brightnessValue);
                        }
                    }
                        if (((MS08) device).getMotionSensorValue() != null) {
                            StopMoveType fromDevice = ((MS08) device).getMotionSensorValue();
                            OnOffType sendToUpdate = OnOffType.OFF;
                            if (fromDevice.equals(StopMoveType.MOVE)) {
                                sendToUpdate = OnOffType.ON;
                            }
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_MOTIONSSENSOR),
                                    sendToUpdate);
                        } {
                        var dryContact1Value = ((MS08) device).getDryContact1Value();
                        if (dryContact1Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT1),
                                    dryContact1Value);
                        }
                    } {
                        var dryContact2Value = ((MS08) device).getDryContact2Value();
                        if (dryContact2Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT2),
                                    dryContact2Value);
                        }
                    }
                        break;
                    case MS12_2C: {
                        var temperatureValue = ((MS12) device).getTemperatureValue();
                        if (temperatureValue != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_TEMPERATUR),
                                    temperatureValue);
                        }
                    } {
                        var brightnessValue = ((MS12) device).getBrightnessValue();
                        if (brightnessValue != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_BRIGHTNESS),
                                    brightnessValue);
                        }
                    }
                        if (((MS12) device).getMotionSensorValue() != null) {
                            StopMoveType fromDevice = ((MS12) device).getMotionSensorValue();
                            OnOffType sendToUpdate = OnOffType.OFF;
                            if (fromDevice.equals(StopMoveType.MOVE)) {
                                sendToUpdate = OnOffType.ON;
                            }
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_MOTIONSSENSOR),
                                    sendToUpdate);
                        }
                        if (((MS12) device).getSonicValue() != null) {
                            StopMoveType fromDevice = ((MS12) device).getSonicValue();
                            OnOffType sendToUpdate = OnOffType.OFF;
                            if (fromDevice.equals(StopMoveType.MOVE)) {
                                sendToUpdate = OnOffType.ON;
                            }
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_SONIC),
                                    sendToUpdate);
                        } {
                        var dryContact1Value = ((MS12) device).getDryContact1Value();
                        if (dryContact1Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT1),
                                    dryContact1Value);
                        }
                    } {
                        var dryContact2Value = ((MS12) device).getDryContact2Value();
                        if (dryContact2Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT2),
                                    dryContact2Value);
                        }
                    } {
                        var relayCh01State = ((MS12) device).getRelayCh01State();
                        if (relayCh01State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH1),
                                    relayCh01State);
                        }
                    } {
                        var relayCh02State = ((MS12) device).getRelayCh02State();
                        if (relayCh02State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH2),
                                    relayCh02State);
                        }
                    }
                        break;
                    case MPL8_48_FH: {
                        var temperatureValue = ((MPL848FH) device).getTemperatureValue();
                        if (temperatureValue != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_TEMPERATUR),
                                    temperatureValue);
                        }
                    }
                        var floorHeatingSetNormalTemperatur = ((MPL848FH) device).getFloorHeatingSetNormalTemperatur();
                        if (floorHeatingSetNormalTemperatur != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHNORMALTEMPSET),
                                    floorHeatingSetNormalTemperatur);
                        } {
                        var floorHeatingSetAwayTemperatur = ((MPL848FH) device).getFloorHeatingSetAwayTemperatur();
                        if (floorHeatingSetAwayTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHAWAYTEMPSET),
                                    floorHeatingSetAwayTemperatur);
                        }
                    } {
                        var floorHeatingSetDayTemperatur = ((MPL848FH) device).getFloorHeatingSetDayTemperatur();
                        if (floorHeatingSetDayTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHDAYTEMPSET),
                                    floorHeatingSetDayTemperatur);
                        }
                    } {
                        var floorHeatingSetNightTemperatur = ((MPL848FH) device).getFloorHeatingSetNightTemperatur();
                        if (floorHeatingSetNightTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHNIGHTTEMPSET),
                                    floorHeatingSetNightTemperatur);
                        }
                    }
                        var floorHeatingCurrentTemperatur = ((MPL848FH) device).getFloorHeatingCurrentTemperatur();
                        if (floorHeatingCurrentTemperatur != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHCURRENTTEMPSET),
                                    floorHeatingCurrentTemperatur);
                        }
                        var floorHeatingMode = ((MPL848FH) device).getFloorHeatingMode();
                        if (floorHeatingMode != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHMODE),
                                    new StringType(floorHeatingMode.toString()));
                        } {
                        var floorHeatingTemperaturType = ((MPL848FH) device).getFloorHeatingTemperaturType();
                        if (floorHeatingTemperaturType != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHTEMPERATURTYPE),
                                    new StringType(floorHeatingTemperaturType));
                        }
                    } {
                        var floorHeatingTimer = ((MPL848FH) device).getFloorHeatingTimer();
                        if (floorHeatingTimer != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHTIMER),
                                    new StringType(floorHeatingTimer));
                        }
                    } {
                        var aCAutoTemperatur = ((MPL848FH) device).getACAutoTemperatur();
                        if (aCAutoTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_ACAUTOTEMPSET),
                                    aCAutoTemperatur);
                        }
                    }
                        var aCCoolingTemperatur = ((MPL848FH) device).getACCoolingTemperatur();
                        if (aCCoolingTemperatur != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_ACCOOLINGTEMPSET),
                                    aCCoolingTemperatur);
                        }
                        var aCCurrentTemperatur = ((MPL848FH) device).getACCurrentTemperatur();
                        if (aCCurrentTemperatur != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_ACCURRENTTEMPSET),
                                    aCCurrentTemperatur);
                        } {
                        var aCDryTemperatur = ((MPL848FH) device).getACDryTemperatur();
                        if (aCDryTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_ACDRYTEMPSET),
                                    aCDryTemperatur);
                        }
                    } {
                        var aCHeatTemperatur = ((MPL848FH) device).getACHeatTemperatur();
                        if (aCHeatTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_ACHEATTEMPSET),
                                    aCHeatTemperatur);
                        }
                    }
                        if (((MPL848FH) device).getACFanSpeed() != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_ACFANSPEED),
                                    new StringType(((MPL848FH) device).getACFanSpeed()));
                        }
                        if (((MPL848FH) device).getACMode() != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_ACMODE),
                                    new StringType(((MPL848FH) device).getACMode()));
                        }
                        OnOffType uvSwitch1 = ((MPL848FH) device).getUVSwitch1();
                        if (uvSwitch1 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH1),
                                    uvSwitch1);
                        }
                        OnOffType uvSwitch2 = ((MPL848FH) device).getUVSwitch2();
                        if (uvSwitch2 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH2),
                                    uvSwitch2);
                        }
                        OnOffType uvSwitch3 = ((MPL848FH) device).getUVSwitch3();
                        if (uvSwitch3 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH3),
                                    uvSwitch3);
                        }
                        OnOffType uvSwitch4 = ((MPL848FH) device).getUVSwitch4();
                        if (uvSwitch4 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH4),
                                    uvSwitch4);
                        }
                        OnOffType uvSwitch5 = ((MPL848FH) device).getUVSwitch5();
                        if (uvSwitch5 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH5),
                                    uvSwitch5);
                        }
                        OnOffType uvSwitch6 = ((MPL848FH) device).getUVSwitch6();
                        if (uvSwitch6 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH6),
                                    uvSwitch6);
                        }
                        break;
                    case MFH06_432: {
                        var temperatureValue = ((MFH06) device).getTemperatureValue();
                        if (temperatureValue != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_TEMPERATUR),
                                    temperatureValue);
                        }
                    } {
                        var mfh06SetNormalTemperatur = ((MFH06) device).getFloorHeatingSetNormalTemperatur();
                        if (mfh06SetNormalTemperatur != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHNORMALTEMPSET),
                                    mfh06SetNormalTemperatur);
                        }
                    } {
                        var floorHeatingSetAwayTemperatur = ((MFH06) device).getFloorHeatingSetAwayTemperatur();
                        if (floorHeatingSetAwayTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHAWAYTEMPSET),
                                    floorHeatingSetAwayTemperatur);
                        }
                    } {
                        var floorHeatingSetDayTemperatur = ((MFH06) device).getFloorHeatingSetDayTemperatur();
                        if (floorHeatingSetDayTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHDAYTEMPSET),
                                    floorHeatingSetDayTemperatur);
                        }
                    } {
                        var floorHeatingSetNightTemperatur = ((MFH06) device).getFloorHeatingSetNightTemperatur();
                        if (floorHeatingSetNightTemperatur != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHNIGHTTEMPSET),
                                    floorHeatingSetNightTemperatur);
                        }
                    } {
                        var mfh06CurrentTemperatur = ((MFH06) device).getFloorHeatingCurrentTemperatur();
                        if (mfh06CurrentTemperatur != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHCURRENTTEMPSET),
                                    mfh06CurrentTemperatur);
                        }
                        var mfh06Mode = ((MFH06) device).getFloorHeatingMode();
                        if (mfh06Mode != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_FHMODE),
                                    new StringType(mfh06Mode.toString()));
                        }
                    }

                        org.openhab.core.library.types.OnOffType u1 = ((MFH06) device).getUVSwitch1();
                        if (u1 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH1), u1);
                        }
                        org.openhab.core.library.types.OnOffType u2 = ((MFH06) device).getUVSwitch2();
                        if (u2 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH2), u2);
                        }
                        org.openhab.core.library.types.OnOffType u3 = ((MFH06) device).getUVSwitch3();
                        if (u3 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH3), u3);
                        }
                        org.openhab.core.library.types.OnOffType u4 = ((MFH06) device).getUVSwitch4();
                        if (u4 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH4), u4);
                        }
                        org.openhab.core.library.types.OnOffType u5 = ((MFH06) device).getUVSwitch5();
                        if (u5 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH5), u5);
                        }
                        org.openhab.core.library.types.OnOffType u6 = ((MFH06) device).getUVSwitch6();
                        if (u6 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH6), u6);
                        }
                        break;
                    case MDT0601_233:
                        PercentType md1 = ((MDT0601) device).getDimChannel1State();
                        if (md1 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL1),
                                    md1);
                        }
                        PercentType md2 = ((MDT0601) device).getDimChannel2State();
                        if (md2 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL2),
                                    md2);
                        }
                        PercentType md3 = ((MDT0601) device).getDimChannel3State();
                        if (md3 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL3),
                                    md3);
                        }
                        PercentType md4 = ((MDT0601) device).getDimChannel4State();
                        if (md4 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL4),
                                    md4);
                        }
                        PercentType md5 = ((MDT0601) device).getDimChannel5State();
                        if (md5 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL5),
                                    md5);
                        }
                        PercentType md6 = ((MDT0601) device).getDimChannel6State();
                        if (md6 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL6),
                                    md6);
                        }
                        break;
                    case MDT04015_433:
                        PercentType m1 = ((MDT04015) device).getDimChannel1State();
                        if (m1 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL1),
                                    m1);
                        }
                        PercentType m2 = ((MDT04015) device).getDimChannel2State();
                        if (m2 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL2),
                                    m2);
                        }
                        PercentType m3 = ((MDT04015) device).getDimChannel3State();
                        if (m3 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL3),
                                    m3);
                        }
                        PercentType m4 = ((MDT04015) device).getDimChannel4State();
                        if (m4 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL4),
                                    m4);
                        }
                        break;
                    case MRDA06:
                        PercentType mr1 = ((MRDA06) device).getDimChannel1State();
                        if (mr1 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL1),
                                    mr1);
                        }
                        PercentType mr2 = ((MRDA06) device).getDimChannel2State();
                        if (mr2 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL2),
                                    mr2);
                        }
                        PercentType mr3 = ((MRDA06) device).getDimChannel3State();
                        if (mr3 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL3),
                                    mr3);
                        }
                        PercentType mr4 = ((MRDA06) device).getDimChannel4State();
                        if (mr4 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL4),
                                    mr4);
                        }
                        PercentType mr5 = ((MRDA06) device).getDimChannel5State();
                        if (mr5 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL5),
                                    mr5);
                        }
                        PercentType mr6 = ((MRDA06) device).getDimChannel6State();
                        if (mr6 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DIMCHANNEL6),
                                    mr6);
                        }
                        break;
                    case MR1610_433: {
                        var relayCh01State = ((MR16xx) device).getRelayCh01State();
                        if (relayCh01State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH1),
                                    relayCh01State);
                        }
                    } {
                        var relayCh02State = ((MR16xx) device).getRelayCh02State();
                        if (relayCh02State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH2),
                                    relayCh02State);
                        }
                    } {
                        var relayCh03State = ((MR16xx) device).getRelayCh03State();
                        if (relayCh03State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH3),
                                    relayCh03State);
                        }
                    } {
                        var relayCh04State = ((MR16xx) device).getRelayCh04State();
                        if (relayCh04State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH4),
                                    relayCh04State);
                        }
                    } {
                        var relayCh05State = ((MR16xx) device).getRelayCh05State();
                        if (relayCh05State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH5),
                                    relayCh05State);
                        }
                    } {
                        var relayCh06State = ((MR16xx) device).getRelayCh06State();
                        if (relayCh06State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH6),
                                    relayCh06State);
                        }
                    } {
                        var relayCh07State = ((MR16xx) device).getRelayCh07State();
                        if (relayCh07State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH7),
                                    relayCh07State);
                        }
                    } {
                        var relayCh08State = ((MR16xx) device).getRelayCh08State();
                        if (relayCh08State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH8),
                                    relayCh08State);
                        }
                    } {
                        var relayCh09State = ((MR16xx) device).getRelayCh09State();
                        if (relayCh09State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH9),
                                    relayCh09State);
                        }
                    } {
                        var relayCh10State = ((MR16xx) device).getRelayCh10State();
                        if (relayCh10State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH10),
                                    relayCh10State);
                        }
                    } {
                        var relayCh11State = ((MR16xx) device).getRelayCh11State();
                        if (relayCh11State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH11),
                                    relayCh11State);
                        }
                    } {
                        var relayCh12State = ((MR16xx) device).getRelayCh12State();
                        if (relayCh12State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH12),
                                    relayCh12State);
                        }
                    } {
                        var relayCh13State = ((MR16xx) device).getRelayCh13State();
                        if (relayCh13State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH13),
                                    relayCh13State);
                        }
                    } {
                        var relayCh14State = ((MR16xx) device).getRelayCh14State();
                        if (relayCh14State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH14),
                                    relayCh14State);
                        }
                    } {
                        var relayCh15State = ((MR16xx) device).getRelayCh15State();
                        if (relayCh15State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH15),
                                    relayCh15State);
                        }
                    } {
                        var relayCh16State = ((MR16xx) device).getRelayCh16State();
                        if (relayCh16State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH16),
                                    relayCh16State);
                        }
                    }
                        break;
                    case MR1216_233:
                    case MR1210_433: {
                        var relayCh01State = ((MR12xx) device).getRelayCh01State();
                        if (relayCh01State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH1),
                                    relayCh01State);
                        }
                    } {
                        var relayCh02State = ((MR12xx) device).getRelayCh02State();
                        if (relayCh02State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH2),
                                    relayCh02State);
                        }
                    } {
                        var relayCh03State = ((MR12xx) device).getRelayCh03State();
                        if (relayCh03State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH3),
                                    relayCh03State);
                        }
                    } {
                        var relayCh04State = ((MR12xx) device).getRelayCh04State();
                        if (relayCh04State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH4),
                                    relayCh04State);
                        }
                    } {
                        var relayCh05State = ((MR12xx) device).getRelayCh05State();
                        if (relayCh05State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH5),
                                    relayCh05State);
                        }
                    } {
                        var relayCh06State = ((MR12xx) device).getRelayCh06State();
                        if (relayCh06State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH6),
                                    relayCh06State);
                        }
                    } {
                        var relayCh07State = ((MR12xx) device).getRelayCh07State();
                        if (relayCh07State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH7),
                                    relayCh07State);
                        }
                    } {
                        var relayCh08State = ((MR12xx) device).getRelayCh08State();
                        if (relayCh08State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH8),
                                    relayCh08State);
                        }
                    } {
                        var relayCh09State = ((MR12xx) device).getRelayCh09State();
                        if (relayCh09State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH9),
                                    relayCh09State);
                        }
                    } {
                        var relayCh10State = ((MR12xx) device).getRelayCh10State();
                        if (relayCh10State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH10),
                                    relayCh10State);
                        }
                    } {
                        var relayCh11State = ((MR12xx) device).getRelayCh11State();
                        if (relayCh11State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH11),
                                    relayCh11State);
                        }
                    } {
                        var relayCh12State = ((MR12xx) device).getRelayCh12State();
                        if (relayCh12State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH12),
                                    relayCh12State);
                        }
                    }
                        break;
                    case MR0816_432:
                    case MR0810_432: {
                        var relayCh01State = ((MR08xx) device).getRelayCh01State();
                        if (relayCh01State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH1),
                                    relayCh01State);
                        }
                    } {
                        var relayCh02State = ((MR08xx) device).getRelayCh02State();
                        if (relayCh02State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH2),
                                    relayCh02State);
                        }
                    } {
                        var relayCh03State = ((MR08xx) device).getRelayCh03State();
                        if (relayCh03State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH3),
                                    relayCh03State);
                        }
                    } {
                        var relayCh04State = ((MR08xx) device).getRelayCh04State();
                        if (relayCh04State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH4),
                                    relayCh04State);
                        }
                    } {
                        var relayCh05State = ((MR08xx) device).getRelayCh05State();
                        if (relayCh05State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH5),
                                    relayCh05State);
                        }
                    } {
                        var relayCh06State = ((MR08xx) device).getRelayCh06State();
                        if (relayCh06State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH6),
                                    relayCh06State);
                        }
                    } {
                        var relayCh07State = ((MR08xx) device).getRelayCh07State();
                        if (relayCh07State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH7),
                                    relayCh07State);
                        }
                    } {
                        var relayCh08State = ((MR08xx) device).getRelayCh08State();
                        if (relayCh08State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH8),
                                    relayCh08State);
                        }
                    }
                        break;
                    case MR0416_C:
                    case MR0416_231:
                    case MR0416_431:
                    case MR0410_431: {
                        var relayCh01State = ((MR04xx) device).getRelayCh01State();
                        if (relayCh01State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH1),
                                    relayCh01State);
                        }
                    } {
                        var relayCh02State = ((MR04xx) device).getRelayCh02State();
                        if (relayCh02State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH2),
                                    relayCh02State);
                        }
                    } {
                        var relayCh03State = ((MR04xx) device).getRelayCh03State();
                        if (relayCh03State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH3),
                                    relayCh03State);
                        }
                    } {
                        var relayCh04State = ((MR04xx) device).getRelayCh04State();
                        if (relayCh04State != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_RELAYCH4),
                                    relayCh04State);
                        }
                    }

                        break;
                    case ML01: {
                        ML01 ml = (ML01) device;
                        var dateSetpoint = ml.getDateSetpoint();
                        if (dateSetpoint != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_TIME),
                                    new DateTimeType(
                                            ZonedDateTime.ofInstant(dateSetpoint.toInstant(), ZoneId.systemDefault())));
                        }
                        org.openhab.core.library.types.OnOffType u200 = ml.getUVSwitch200();
                        if (u200 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH200),
                                    u200);
                        }
                        org.openhab.core.library.types.OnOffType u201 = ml.getUVSwitch201();
                        if (u201 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH201),
                                    u201);
                        }
                        org.openhab.core.library.types.OnOffType u202 = ml.getUVSwitch202();
                        if (u202 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH202),
                                    u202);
                        }
                        org.openhab.core.library.types.OnOffType u203 = ml.getUVSwitch203();
                        if (u203 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH203),
                                    u203);
                        }
                        org.openhab.core.library.types.OnOffType u204 = ml.getUVSwitch204();
                        if (u204 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH204),
                                    u204);
                        }
                        org.openhab.core.library.types.OnOffType u205 = ml.getUVSwitch205();
                        if (u205 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH205),
                                    u205);
                        }
                        org.openhab.core.library.types.OnOffType u206 = ml.getUVSwitch206();
                        if (u206 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH206),
                                    u206);
                        }
                        org.openhab.core.library.types.OnOffType u207 = ml.getUVSwitch207();
                        if (u207 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH207),
                                    u207);
                        }
                        org.openhab.core.library.types.OnOffType u208 = ml.getUVSwitch208();
                        if (u208 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH208),
                                    u208);
                        }
                        org.openhab.core.library.types.OnOffType u209 = ml.getUVSwitch209();
                        if (u209 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH209),
                                    u209);
                        }
                        org.openhab.core.library.types.OnOffType u210 = ml.getUVSwitch210();
                        if (u210 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH210),
                                    u210);
                        }
                        org.openhab.core.library.types.OnOffType u211 = ml.getUVSwitch211();
                        if (u211 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH211),
                                    u211);
                        }
                        org.openhab.core.library.types.OnOffType u212 = ml.getUVSwitch212();
                        if (u212 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH212),
                                    u212);
                        }
                        org.openhab.core.library.types.OnOffType u213 = ml.getUVSwitch213();
                        if (u213 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH213),
                                    u213);
                        }
                        org.openhab.core.library.types.OnOffType u214 = ml.getUVSwitch214();
                        if (u214 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH214),
                                    u214);
                        }
                        org.openhab.core.library.types.OnOffType u215 = ml.getUVSwitch215();
                        if (u215 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH215),
                                    u215);
                        }
                        org.openhab.core.library.types.OnOffType u216 = ml.getUVSwitch216();
                        if (u216 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH216),
                                    u216);
                        }
                        org.openhab.core.library.types.OnOffType u217 = ml.getUVSwitch217();
                        if (u217 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH217),
                                    u217);
                        }
                        org.openhab.core.library.types.OnOffType u218 = ml.getUVSwitch218();
                        if (u218 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH218),
                                    u218);
                        }
                        org.openhab.core.library.types.OnOffType u219 = ml.getUVSwitch219();
                        if (u219 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH219),
                                    u219);
                        }
                        org.openhab.core.library.types.OnOffType u220 = ml.getUVSwitch220();
                        if (u220 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH220),
                                    u220);
                        }
                        org.openhab.core.library.types.OnOffType u221 = ml.getUVSwitch221();
                        if (u221 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH221),
                                    u221);
                        }
                        org.openhab.core.library.types.OnOffType u222 = ml.getUVSwitch222();
                        if (u222 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH222),
                                    u222);
                        }
                        org.openhab.core.library.types.OnOffType u223 = ml.getUVSwitch223();
                        if (u223 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH223),
                                    u223);
                        }
                        org.openhab.core.library.types.OnOffType u224 = ml.getUVSwitch224();
                        if (u224 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH224),
                                    u224);
                        }
                        org.openhab.core.library.types.OnOffType u225 = ml.getUVSwitch225();
                        if (u225 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH225),
                                    u225);
                        }
                        org.openhab.core.library.types.OnOffType u226 = ml.getUVSwitch226();
                        if (u226 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH226),
                                    u226);
                        }
                        org.openhab.core.library.types.OnOffType u227 = ml.getUVSwitch227();
                        if (u227 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH227),
                                    u227);
                        }
                        org.openhab.core.library.types.OnOffType u228 = ml.getUVSwitch228();
                        if (u228 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH228),
                                    u228);
                        }
                        org.openhab.core.library.types.OnOffType u229 = ml.getUVSwitch229();
                        if (u229 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH229),
                                    u229);
                        }
                        org.openhab.core.library.types.OnOffType u230 = ml.getUVSwitch230();
                        if (u230 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH230),
                                    u230);
                        }
                        org.openhab.core.library.types.OnOffType u231 = ml.getUVSwitch231();
                        if (u231 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH231),
                                    u231);
                        }
                        org.openhab.core.library.types.OnOffType u232 = ml.getUVSwitch232();
                        if (u232 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH232),
                                    u232);
                        }
                        org.openhab.core.library.types.OnOffType u233 = ml.getUVSwitch233();
                        if (u233 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH233),
                                    u233);
                        }
                        org.openhab.core.library.types.OnOffType u234 = ml.getUVSwitch234();
                        if (u234 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH234),
                                    u234);
                        }
                        org.openhab.core.library.types.OnOffType u235 = ml.getUVSwitch235();
                        if (u235 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH235),
                                    u235);
                        }
                        org.openhab.core.library.types.OnOffType u236 = ml.getUVSwitch236();
                        if (u236 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH236),
                                    u236);
                        }
                        org.openhab.core.library.types.OnOffType u237 = ml.getUVSwitch237();
                        if (u237 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH237),
                                    u237);
                        }
                        org.openhab.core.library.types.OnOffType u238 = ml.getUVSwitch238();
                        if (u238 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH238),
                                    u238);
                        }
                        org.openhab.core.library.types.OnOffType u239 = ml.getUVSwitch239();
                        if (u239 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH239),
                                    u239);
                        }
                        org.openhab.core.library.types.OnOffType u240 = ml.getUVSwitch240();
                        if (u240 != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_UVSWITCH240),
                                    u240);
                        }
                    }
                        break;
                    case MS24: {
                        var dryContact1Value = ((MS24) device).getDryContact1Value();
                        if (dryContact1Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT1),
                                    dryContact1Value);
                        }
                    } {
                        var dryContact2Value = ((MS24) device).getDryContact2Value();
                        if (dryContact2Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT2),
                                    dryContact2Value);
                        }
                    } {
                        var dryContact3Value = ((MS24) device).getDryContact3Value();
                        if (dryContact3Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT3),
                                    dryContact3Value);
                        }
                    } {
                        var dryContact4Value = ((MS24) device).getDryContact4Value();
                        if (dryContact4Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT4),
                                    dryContact4Value);
                        }
                    } {
                        var dryContact5Value = ((MS24) device).getDryContact5Value();
                        if (dryContact5Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT5),
                                    dryContact5Value);
                        }
                    } {
                        var dryContact6Value = ((MS24) device).getDryContact6Value();
                        if (dryContact6Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT6),
                                    dryContact6Value);
                        }
                    } {
                        var dryContact7Value = ((MS24) device).getDryContact7Value();
                        if (dryContact7Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT7),
                                    dryContact7Value);
                        }
                    } {
                        var dryContact8Value = ((MS24) device).getDryContact8Value();
                        if (dryContact8Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT8),
                                    dryContact8Value);
                        }
                    } {
                        var dryContact9Value = ((MS24) device).getDryContact9Value();
                        if (dryContact9Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT9),
                                    dryContact9Value);
                        }
                    } {
                        var dryContact10Value = ((MS24) device).getDryContact10Value();
                        if (dryContact10Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT10),
                                    dryContact10Value);
                        }
                    } {
                        var dryContact11Value = ((MS24) device).getDryContact11Value();
                        if (dryContact11Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT11),
                                    dryContact11Value);
                        }
                    } {
                        var dryContact12Value = ((MS24) device).getDryContact12Value();
                        if (dryContact12Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT12),
                                    dryContact12Value);
                        }
                    } {
                        var dryContact13Value = ((MS24) device).getDryContact13Value();
                        if (dryContact13Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT13),
                                    dryContact13Value);
                        }
                    } {
                        var dryContact14Value = ((MS24) device).getDryContact14Value();
                        if (dryContact14Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT14),
                                    dryContact14Value);
                        }
                    } {
                        var dryContact15Value = ((MS24) device).getDryContact15Value();
                        if (dryContact15Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT15),
                                    dryContact15Value);
                        }
                    } {
                        var dryContact16Value = ((MS24) device).getDryContact16Value();
                        if (dryContact16Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT16),
                                    dryContact16Value);
                        }
                    } {
                        var dryContact17Value = ((MS24) device).getDryContact17Value();
                        if (dryContact17Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT17),
                                    dryContact17Value);
                        }
                    } {
                        var dryContact18Value = ((MS24) device).getDryContact18Value();
                        if (dryContact18Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT18),
                                    dryContact18Value);
                        }
                    } {
                        var dryContact19Value = ((MS24) device).getDryContact19Value();
                        if (dryContact19Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT19),
                                    dryContact19Value);
                        }
                    } {
                        var dryContact20Value = ((MS24) device).getDryContact20Value();
                        if (dryContact20Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT20),
                                    dryContact20Value);
                        }
                    } {
                        var dryContact21Value = ((MS24) device).getDryContact21Value();
                        if (dryContact21Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT21),
                                    dryContact21Value);
                        }
                    } {
                        var dryContact22Value = ((MS24) device).getDryContact22Value();
                        if (dryContact22Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT22),
                                    dryContact22Value);
                        }
                    } {
                        var dryContact23Value = ((MS24) device).getDryContact23Value();
                        if (dryContact23Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT23),
                                    dryContact23Value);
                        }
                    } {
                        var dryContact24Value = ((MS24) device).getDryContact24Value();
                        if (dryContact24Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_DRYCONTACT24),
                                    dryContact24Value);
                        }
                    }
                        break;
                    case MW02: {
                        // StopMoveType (unlike UpDownType) isn't a valid channel State, only a Command, so a
                        // Stop event (see MW02#handleCurtainSwitchStatus) has no state of its own to push here.
                        var upDownShutter1Status = ((MW02) device).getUpDownShutter1Status();
                        if (upDownShutter1Status != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_SHUTTER1CONTROL),
                                    upDownShutter1Status);
                        }
                    } {
                        var upDownShutter2Status = ((MW02) device).getUpDownShutter2Status();
                        if (upDownShutter2Status != null) {
                            updateState(
                                    new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_SHUTTER2CONTROL),
                                    upDownShutter2Status);
                        }
                    }
                        break;
                    case MPT04_48: {
                        var button1Value = ((MPT0448) device).getbutton1Value();
                        if (button1Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_BUTTON1),
                                    button1Value);
                        }
                    } {
                        var button2Value = ((MPT0448) device).getbutton2Value();
                        if (button2Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_BUTTON2),
                                    button2Value);
                        }
                    } {
                        var button3Value = ((MPT0448) device).getbutton3Value();
                        if (button3Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_BUTTON3),
                                    button3Value);
                        }
                    } {
                        var button4Value = ((MPT0448) device).getbutton4Value();
                        if (button4Value != null) {
                            updateState(new ChannelUID(getThing().getUID(), HdlBindingConstants.CHANNEL_BUTTON4),
                                    button4Value);
                        }
                    } {
                        // Event-only refresh (refreshRate == -1): the panel just actively controlled
                        // something (see MPT0448#treatHDLPacketForDevice), so request a fresh status read
                        // instead of waiting on a fixed-delay poll.
                        var hdlBridge = bridgeHandler;
                        if (refreshRate == -1 && hdlBridge != null && ((MPT0448) device).consumeControlEvent()) {
                            sendMpt04StatusProbe(hdlBridge);
                        }
                    }
                        break;
                    default:
                        logger.debug("Device Type: {} unhandled", device.getType());
                        break;
                }
                device.setUpdated(false);
            } else {
                logger.debug("No changes for {} {} id: {}", device.getType(), device.getSerialNr(),
                        getThing().getUID());
            }
        }
    }

    @Override
    public void onDeviceRemoved(HdlBridgeHandler bridge, Device device) {
        if (device.getSerialNr().equals(hdldeviceSerial)) {
            bridgeHandler.unregisterDeviceStatusListener(this);
            bridgeHandler = null;
            // forceRefresh = true;
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    @Override
    public void onDeviceAdded(Bridge bridge, Device device) {
        // forceRefresh = true;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.smarthome.core.thing.binding.BaseThingHandler#bridgeStatusChanged(org.eclipse.smarthome.core.thing.
     * ThingStatusInfo)
     */
    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge Status updated to {} for device: {}", bridgeStatusInfo.getStatus().toString(),
                getThing().getUID().toString());
        if (!bridgeStatusInfo.getStatus().equals(ThingStatus.ONLINE)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    /**
     * Set the Configurable properties for this device
     *
     * @param device
     */
    private void setDeviceConfiguration(Device device) {
        try {
            logger.debug("HDL! {} {} configuration update", device.getType().toString(), device.getSerialNr());
            Configuration configuration = editConfiguration();

            // Add additional device config entries
            for (Map.Entry<String, Object> entry : device.getProperties().entrySet()) {
                configuration.put(entry.getKey(), entry.getValue());
            }
            updateConfiguration(configuration);
            logger.debug("Config updated: {}", configuration.getProperties());
            // configSet = true;
        } catch (Exception e) {
            logger.debug("Exception occurred during configuration edit: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onDeviceConfigUpdate(Bridge bridge, Device device) {
        if (device.getSerialNr().equals(hdldeviceSerial)) {
            setDeviceConfiguration(device);
        }
    }
}
