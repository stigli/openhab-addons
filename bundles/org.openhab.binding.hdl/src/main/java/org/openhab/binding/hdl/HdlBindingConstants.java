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
package org.openhab.binding.hdl;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

//import com.google.common.collect.ImmutableSet;
//import com.google.common.collect.Lists;

/**
 * The {@link HdlBinding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class HdlBindingConstants {

    public static final String BINDING_ID = "hdl";

    public static final String PROPERTY_IP = "Ip";
    public static final String PROPERTY_PORT = "Port";

    public static final String PROPERTY_SUBNET = "Subnet";
    public static final String PROPERTY_DEVICEID = "DeviceID";
    public static final String PROPERTY_CHANNEL = "Channel";
    public static final String PROPERTY_REFRESHRATE = "refreshInterval";
    public static final String PROPERTY_CHANNELNUMBER = "channelNumber";
    public static final String PROPERTY_AREA = "area";
    public static final String PROPERTY_SCENE = "scene";
    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");

    public static final ThingTypeUID THING_TYPE_ML01 = new ThingTypeUID(BINDING_ID, "ML01"); // Dimmer
    public static final ThingTypeUID THING_TYPE_MDT0601 = new ThingTypeUID(BINDING_ID, "MDT0601"); // 6 Ch Uni Dim
    public static final ThingTypeUID THING_TYPE_MDT04015 = new ThingTypeUID(BINDING_ID, "MDT04015"); // 4 Ch Uni Dim
    public static final ThingTypeUID THING_TYPE_MPL8_48_FH = new ThingTypeUID(BINDING_ID, "MPL8_48_FH"); // DLP
                                                                                                         // Bryterpanel
    public static final ThingTypeUID THING_TYPE_MFH06 = new ThingTypeUID(BINDING_ID, "MFH06"); // Floor heating module
    public static final ThingTypeUID THING_TYPE_MPT04_48 = new ThingTypeUID(BINDING_ID, "MPT04"); // 4 Buttons
    public static final ThingTypeUID THING_TYPE_MR16XX = new ThingTypeUID(BINDING_ID, "MR16xx"); // 12 channel relay
    public static final ThingTypeUID THING_TYPE_MR12XX = new ThingTypeUID(BINDING_ID, "MR12xx"); // 12 channel relay
    public static final ThingTypeUID THING_TYPE_MR08XX = new ThingTypeUID(BINDING_ID, "MR08xx"); // 8 channel relay
    public static final ThingTypeUID THING_TYPE_MR04XX = new ThingTypeUID(BINDING_ID, "MR04xx"); // 4 channel relay
    // MRDA0610 is the same 6-channel 0-10V device as MRDA06 (different HDL article number for the same
    // function), so it reuses THING_TYPE_MRDA06 rather than having its own thing-type; see Device.create()
    // and HdlDeviceDiscoveryService.
    public static final ThingTypeUID THING_TYPE_MRDA06 = new ThingTypeUID(BINDING_ID, "MRDA06"); // 6 channels
    public static final ThingTypeUID THING_TYPE_MW02 = new ThingTypeUID(BINDING_ID, "MW02"); // Gardin kontroller
    public static final ThingTypeUID THING_TYPE_MS12 = new ThingTypeUID(BINDING_ID, "MS12"); // 12 i 1
    public static final ThingTypeUID THING_TYPE_MS08 = new ThingTypeUID(BINDING_ID, "MS08"); // 8 i 1 sensor
    public static final ThingTypeUID THING_TYPE_MS24 = new ThingTypeUID(BINDING_ID, "MS24"); // Sensor Input Module
    // Virtual thing - not a real device type on the bus, so it's not part of SUPPORTED_DEVICE_THING_TYPES_UIDS
    // (discovery); triggers an existing scene programmed via the HDL Setup Tool, doesn't represent hardware.
    public static final ThingTypeUID THING_TYPE_SCENE = new ThingTypeUID(BINDING_ID, "Scene");

    // List of all Channel ids
    public static final String CHANNEL_BUS_MESSAGE_RATE = "BusMessageRate";
    public static final String CHANNEL_BUS_INVALID_PACKET_COUNT = "BusInvalidPacketCount";
    public static final String CHANNEL_TEMPERATUR = "temperature";
    public static final String CHANNEL_TIME = "time";
    public static final String CHANNEL_BRIGHTNESS = "Brightness";
    public static final String CHANNEL_MOTIONSSENSOR = "MotionSensor";
    public static final String CHANNEL_SONIC = "Sonic";
    public static final String CHANNEL_BUTTON1 = "Button1";
    public static final String CHANNEL_BUTTON2 = "Button2";
    public static final String CHANNEL_BUTTON3 = "Button3";
    public static final String CHANNEL_BUTTON4 = "Button4";
    public static final String CHANNEL_SCENETRIGGER = "Trigger";
    public static final String CHANNEL_FHNORMALTEMPSET = "FHNormalTempSet";
    // Matches the "FHTempSet" channel id actually declared in thing-types.xml for both MPL8_48_FH and MFH06
    // (the Java constant name keeps "Day" for readability at call sites; the string value must not).
    public static final String CHANNEL_FHDAYTEMPSET = "FHTempSet";
    public static final String CHANNEL_FHNIGHTTEMPSET = "FHNightTempSet";
    public static final String CHANNEL_FHAWAYTEMPSET = "FHAwayTempSet";
    public static final String CHANNEL_FHCURRENTTEMPSET = "FHCurrentTempSet";
    public static final String CHANNEL_FHMODE = "FHMode";
    public static final String CHANNEL_FHTEMPERATURTYPE = "FHTemperaturType";
    public static final String CHANNEL_FHTIMER = "FHTimer";
    public static final String CHANNEL_ACMODE = "ACMode";
    public static final String CHANNEL_ACFANSPEED = "ACFanSpeed";
    public static final String CHANNEL_MUSICCOMMAND = "MusicCommand";
    public static final String CHANNEL_ACCOOLINGTEMPSET = "ACCoolingTempSet";
    public static final String CHANNEL_ACHEATTEMPSET = "ACHeatTempSet";
    public static final String CHANNEL_ACAUTOTEMPSET = "ACAutoTempSet";
    public static final String CHANNEL_ACDRYTEMPSET = "ACDryTempSet";
    public static final String CHANNEL_ACCURRENTTEMPSET = "ACCurrentTempSet";
    // public static final String CHANNEL_SHUTTER1UPDOWN = "Shutter1UpDown";
    // public static final String CHANNEL_SHUTTER2UPDOWN = "Shutter2UpDown";
    public static final String CHANNEL_SHUTTER1CONTROL = "Shutter1Control";
    public static final String CHANNEL_SHUTTER2CONTROL = "Shutter2Control";
    public static final String CHANNEL_CURTAIN1DURATION = "Curtain1Duration";
    public static final String CHANNEL_CURTAIN2DURATION = "Curtain2Duration";

    public static final String CHANNEL_DRYCONTACT1 = "DryContact1Status";
    public static final String CHANNEL_DRYCONTACT2 = "DryContact2Status";
    public static final String CHANNEL_DRYCONTACT3 = "DryContact3Status";
    public static final String CHANNEL_DRYCONTACT4 = "DryContact4Status";
    public static final String CHANNEL_DRYCONTACT5 = "DryContact5Status";
    public static final String CHANNEL_DRYCONTACT6 = "DryContact6Status";
    public static final String CHANNEL_DRYCONTACT7 = "DryContact7Status";
    public static final String CHANNEL_DRYCONTACT8 = "DryContact8Status";
    public static final String CHANNEL_DRYCONTACT9 = "DryContact9Status";
    public static final String CHANNEL_DRYCONTACT10 = "DryContact10Status";
    public static final String CHANNEL_DRYCONTACT11 = "DryContact11Status";
    public static final String CHANNEL_DRYCONTACT12 = "DryContact12Status";
    public static final String CHANNEL_DRYCONTACT13 = "DryContact13Status";
    public static final String CHANNEL_DRYCONTACT14 = "DryContact14Status";
    public static final String CHANNEL_DRYCONTACT15 = "DryContact15Status";
    public static final String CHANNEL_DRYCONTACT16 = "DryContact16Status";
    public static final String CHANNEL_DRYCONTACT17 = "DryContact17Status";
    public static final String CHANNEL_DRYCONTACT18 = "DryContact18Status";
    public static final String CHANNEL_DRYCONTACT19 = "DryContact19Status";
    public static final String CHANNEL_DRYCONTACT20 = "DryContact20Status";
    public static final String CHANNEL_DRYCONTACT21 = "DryContact21Status";
    public static final String CHANNEL_DRYCONTACT22 = "DryContact22Status";
    public static final String CHANNEL_DRYCONTACT23 = "DryContact23Status";
    public static final String CHANNEL_DRYCONTACT24 = "DryContact24Status";
    public static final String CHANNEL_DIMCHANNEL1 = "DimChannel1";
    public static final String CHANNEL_DIMCHANNEL2 = "DimChannel2";
    public static final String CHANNEL_DIMCHANNEL3 = "DimChannel3";
    public static final String CHANNEL_DIMCHANNEL4 = "DimChannel4";
    public static final String CHANNEL_DIMCHANNEL5 = "DimChannel5";
    public static final String CHANNEL_DIMCHANNEL6 = "DimChannel6";
    public static final String CHANNEL_RELAYCH1 = "RelayCh1";
    public static final String CHANNEL_RELAYCH2 = "RelayCh2";
    public static final String CHANNEL_RELAYCH3 = "RelayCh3";
    public static final String CHANNEL_RELAYCH4 = "RelayCh4";
    public static final String CHANNEL_RELAYCH5 = "RelayCh5";
    public static final String CHANNEL_RELAYCH6 = "RelayCh6";
    public static final String CHANNEL_RELAYCH7 = "RelayCh7";
    public static final String CHANNEL_RELAYCH8 = "RelayCh8";
    public static final String CHANNEL_RELAYCH9 = "RelayCh9";
    public static final String CHANNEL_RELAYCH10 = "RelayCh10";
    public static final String CHANNEL_RELAYCH11 = "RelayCh11";
    public static final String CHANNEL_RELAYCH12 = "RelayCh12";
    public static final String CHANNEL_RELAYCH13 = "RelayCh13";
    public static final String CHANNEL_RELAYCH14 = "RelayCh14";
    public static final String CHANNEL_RELAYCH15 = "RelayCh15";
    public static final String CHANNEL_RELAYCH16 = "RelayCh16";
    // Universal Switch channels are added dynamically per-Thing instead of a fixed per-number list here -
    // how many a device exposes, and what each is assigned to, is a per-installation HDL Setup Tool config
    // choice (see the "UVSwitch" extensible channel-type and its "switchNumber" config parameter).
    public static final String CHANNELTYPE_UVSWITCH = "UVSwitch";
    public static final String CHANNEL_CONFIG_SWITCHNUMBER = "switchNumber";

    public enum EnumFHMode {
        Normal,
        Day,
        Night,
        Away,
        Timer
    }

    public enum DryContactNr {
        DryContact1(1),
        DryContact2(2),
        DryContact3(3),
        DryContact4(4),
        DryContact5(5),
        DryContact6(6),
        DryContact7(7),
        DryContact8(8),
        DryContact9(9),
        DryContact10(10),
        DryContact11(11),
        DryContact12(12),
        DryContact13(13),
        DryContact14(14),
        DryContact15(15),
        DryContact16(16),
        DryContact17(17),
        DryContact18(18),
        DryContact19(19),
        DryContact20(20),
        DryContact21(21),
        DryContact22(22),
        DryContact23(23),
        DryContact24(24);

        private int value;

        private DryContactNr(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum CurtainNr {
        Shutter1Control(1),
        Shutter2Control(2);

        private int value;

        private CurtainNr(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum DimChannelNr {
        DimChannel1(1),
        DimChannel2(2),
        DimChannel3(3),
        DimChannel4(4),
        DimChannel5(5),
        DimChannel6(6);

        private int value;

        private DimChannelNr(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum RelayChannelNr {
        RelayCh1(1),
        RelayCh2(2),
        RelayCh3(3),
        RelayCh4(4),
        RelayCh5(5),
        RelayCh6(6),
        RelayCh7(7),
        RelayCh8(8),
        RelayCh9(9),
        RelayCh10(10),
        RelayCh11(11),
        RelayCh12(12),
        RelayCh13(13),
        RelayCh14(14),
        RelayCh15(15),
        RelayCh16(16);

        private int value;

        private RelayChannelNr(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(THING_TYPE_BRIDGE, THING_TYPE_ML01, THING_TYPE_MDT0601, THING_TYPE_MPL8_48_FH,
                    THING_TYPE_MFH06, THING_TYPE_MPT04_48, THING_TYPE_MR16XX, THING_TYPE_MR12XX, THING_TYPE_MR08XX,
                    THING_TYPE_MW02, THING_TYPE_MS12, THING_TYPE_MS08, THING_TYPE_MS24, THING_TYPE_MRDA06,
                    THING_TYPE_MR04XX, THING_TYPE_MDT04015, THING_TYPE_SCENE).collect(Collectors.toSet()));

    public static final Set<ThingTypeUID> SUPPORTED_DEVICE_THING_TYPES_UIDS = Collections.unmodifiableSet(Stream
            .of(THING_TYPE_ML01, THING_TYPE_MDT0601, THING_TYPE_MPL8_48_FH, THING_TYPE_MFH06, THING_TYPE_MPT04_48,
                    THING_TYPE_MR12XX, THING_TYPE_MR16XX, THING_TYPE_MR08XX, THING_TYPE_MW02, THING_TYPE_MS12,
                    THING_TYPE_MS08, THING_TYPE_MS24, THING_TYPE_MRDA06, THING_TYPE_MR04XX, THING_TYPE_MDT04015)
            .collect(Collectors.toSet()));

    public static final Set<ThingTypeUID> SUPPORTED_BRIDGE_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(THING_TYPE_BRIDGE).collect(Collectors.toSet()));
}
