# HDL Binding

This is the binding for HDL.
This binding allows you to integrate, view and control the HDL  items in the openHAB environment

## Supported Things

This binding supports for now 15 different HDL items.
More will be added as the binding are expanded.
Thing names are using the article number that HDL are using.

| Thing         | Type      | Description                                                   |
|---------------|-----------|-----------------------------------------------------------------|
| bridge        | Bridge    | This is the HDL LAN gateway (MBUS01IP)                    |
| MDT0601       | Thing     | HDL Dimmer 6x1A - Universal                                   |
| MDT04015      | Thing     | HDL Dimmer 4x1.5A - Universal                                 |
| ML01          | Thing     | HDL logic module                                              |
| MPL8_48_FH    | Thing     | HDL Button Panel (DLP) with AC, Music, Clock, Floor Heating   |
| MFH06         | Thing     | HDL Floor Heating Module                                       |
| MPT04         | Thing     | Digital touch switch 4 buttons                                |
| MR16xx        | Thing     | HDL Relay 16 Channel                                           |
| MR12xx        | Thing     | HDL Relay 12 Channel                                           |
| MR08xx        | Thing     | HDL Relay 8 Channel                                            |
| MR04xx        | Thing     | HDL Relay 4 Channel                                            |
| MRDA06        | Thing     | HDL Ballast controller, 6 channels, 0-10V (also covers the MRDA0610 article) |
| MS08          | Thing     | HDL Sensor with 8 functions                                   |
| MS12          | Thing     | HDL Sensor with 12 functions                                  |
| MS24          | Thing     | HDL with 24 dry contacts                                      |
| MW02          | Thing     | HDL Curtain controller for controlling off 3. parts curtains  |

## Discovery

The bridge actively searches the HDL bus for devices once when it comes online, and again whenever a manual
Inbox scan is triggered; devices found this way, as well as any device that is seen sending traffic for other
reasons, show up in the Inbox. Not every discovered device type maps to a supported Thing type yet (see
`HdlDeviceDiscoveryService`), and this is a new, hardware-unverified feature, so things and items may still need
to be defined manually.

## Bus Statistics

The bridge Thing has two channels for trending bus load over time:

| Channel Type ID        | Item Type | Description                                                        |
|-------------------------|-----------|---------------------------------------------------------------------|
| BusMessageRate          | Number    | Average messages/second seen on the bus since the last update (updated every 60s). |
| BusInvalidPacketCount   | Number    | Total count of unparseable/invalid packets seen since the bridge started.           |

For a detailed on-demand breakdown (average/peak rate, total messages, and the top 5 sending and receiving
addresses on the bus), use the console command:

```shell
openhab:hdl busstats
```

## Diagnosing Curtain Commands

Every curtain-related command seen anywhere on the bus (not just ones going through this binding) is logged
at DEBUG level, including the sending device's address - useful for tracking down a panel that is
unexpectedly driving a curtain directly (bypassing openHAB). Enable debug logging for
`org.openhab.binding.hdl` and look for log lines starting with `Curtain command:`.

## HDL Scenes

If a scene configured in the HDL Setup Tool changes a dimmer/relay's channels (whether triggered from a
physical panel, another scene, or anywhere else on the bus), the affected `DimChannel`/`RelayCh` channels on
`MDT0601`, `MDT04015`, `MRDA06`, `MR16xx`, `MR12xx`, `MR08xx`, and `MR04xx` update automatically to reflect
the new state - no separate "Scene" Thing or configuration needed. This binding does not (yet) support
triggering a scene from openHAB itself, only reflecting scenes triggered elsewhere.

This is based on the HDL Buspro `Broadcast_Status_of_Scene` message; the exact byte layout was reconstructed
from third-party protocol documentation rather than confirmed HDL hardware traffic, so double-check it
reflects reality correctly on your own setup after a scene runs.

## Universal Switches (UVSwitch)

HDL Buspro "Universal Switch" (UV Switch) is a generic on/off flag - a physical device can expose any number
of them, and what each one is assigned to is decided per-installation in the HDL Setup Tool, not something
this binding can know in advance. Because of that, `ML01`, `MPL8_48_FH`, and `MFH06` don't declare a fixed
set of UVSwitch channels: they're `extensible` Thing types, and you add exactly the switch numbers you need
directly in the Thing definition, each as its own channel with a `switchNumber` parameter:

```java
Thing ML01 1101 [Subnet=1, DeviceID=101] {
    Type UVSwitch : UVSwitch201 "Alarm Status" [ switchNumber=201 ]
    Type UVSwitch : UVSwitch205 "Something Else" [ switchNumber=205 ]
}
```

**Valid switch number range:** HDL documents the overall Universal Switch range as 1-248. On a Logic module
(`ML01`) specifically, numbers 201-248 are reserved for that purpose (2 per Logic module) - use a number in
that sub-range there, not 1-200. For other panels (`MPL8_48_FH`, `MFH06`), 1-200 is the general range,
though what's actually assigned to a given number is entirely down to your HDL Setup Tool project.

The channel id (`UVSwitch201` above) is yours to choose - it doesn't have to match the switch number, though
keeping them aligned makes `.items` files easier to read. **Note for existing setups:** earlier versions of
this binding declared a fixed list of channels (`UVSwitch1`-`6` on `MPL8_48_FH`, `UVSwitch200`-`240` on
`ML01`) directly in the thing type. If you're upgrading, add explicit `Type UVSwitch : ...` lines (as above)
for whichever switch numbers you were already using, using the same channel id, so your existing `.items`
links keep working without changes.

## Binding Configuration

No binding wide settings.

## Thing Configuration

All things are identified by their Subnet and Device ID number, hence this is mandatory.
The LAN (`bridge` thing) also requires the IP address and Port Number (6000) to be defined.

MFH06 additionally requires a `channelNumber`, since one physical Floor Heating Module has several independent
heating channels, each represented by its own Thing.

MPT04's `refreshInterval` supports a special `-1` value: instead of polling on a fixed interval, the binding only
requests a fresh status right after it sees the panel actively control something (button routed directly to
another device), which is how these panels are commonly configured. `0` disables refresh entirely, and any
positive number polls every that many seconds, same as the other Things below.

## Channels

Depending on the thing it supports different Channels

DryContact(1-24)Status  means that that it can be 24 Dry Contact channels. What is available on that thing is shown under "Available on thing" for instead (1-2) means that channel DryContact1 and DryContact2 is available on that thing. If nothing is set all channels is available on that thing.

| Channel Type ID              | Item Type        | Description                                                | Available on thing                          |
|-------------------------------|------------------|-------------------------------------------------------------|----------------------------------------------|
| DimChannel(1-6)               | Dimmer           | This channel indicates the value of the dimmer.              | MDT0601, MDT04015(1-4), MRDA06                |
| DryContact(1-24)Status        | Contact          | This channel indicates the status of the dry contact.        | MS24, MS08(1-2), MS12(1-2)                    |
| RelayCh(1-16)                 | Switch           | This channel indicates the value of the relay.                | MR16xx(1-16), MR12xx(1-12), MR08xx(1-8), MR04xx(1-4), MS12(1-2) |
| UVSwitch (dynamic)            | Switch           | Universal Switch - add one per switch number you need (see "Universal Switches (UVSwitch)" above); not a fixed channel list.             | ML01, MPL8_48_FH, MFH06                |
| Button(1-4)                   | Switch           | This channel indicates the state of a touch panel button.      | MPT04                                         |
| Brightness                    | Number           | This channel indicates the measured lumen.                    | MS08, MS12                                    |
| MotionSensor                  | Switch           | This channel indicates if there is any movement.               | MS08, MS12                                    |
| Sonic                         | Switch           | This channel indicates if there is any movement.               | MS12                                          |
| temperature                   | Number           | This channel indicates the measured temperature (in °C).       | MPL8_48_FH, MS08, MS12                        |
| time                          | DateTime         | Current time.                                                 | ML01                                          |
| Shutter(1-2)Control           | Rollershutter    | Device control: send UP/DOWN/STOP commands; state reflects the last known UP/DOWN direction (percentage closure is not implemented). | MW02                                          |
| FHMode                        | String           | Floor heating mode (Normal, Day, Night, Away, Timer). Writable on MPL8_48_FH.         | MPL8_48_FH, MFH06                             |
| FHNormalTempSet / FHTempSet / FHNightTempSet / FHAwayTempSet | Number:Temperature | Floor heating setpoint temperatures. Individually writable on MPL8_48_FH.       | MPL8_48_FH, MFH06                             |
| FHCurrentTempSet              | Number:Temperature | Current floor heating temperature.                            | MPL8_48_FH, MFH06                             |
| FHTemperaturType              | String           | Temperature unit (C or F) the panel is reporting setpoints in.  | MPL8_48_FH                                    |
| FHTimer                       | String           | Active schedule period (Day or Night); only meaningful when FHMode is Timer.  | MPL8_48_FH                                    |
| ACMode                        | String           | AC mode (Cooling, Heating, Fan, Auto, Dehumidfy). Read-only - view only, HDL doesn't expose a documented way to control AC via the DLP panel yet.  | MPL8_48_FH                                    |
| ACFanSpeed                    | String           | AC fan speed (Auto, High, Medium, Low). Read-only, see ACMode.                  | MPL8_48_FH                                    |
| ACCoolingTempSet / ACHeatTempSet / ACAutoTempSet / ACDryTempSet | Number:Temperature | AC setpoint temperatures. Read-only, see ACMode.        | MPL8_48_FH                                    |
| ACCurrentTempSet              | Number:Temperature | Current AC temperature.                                        | MPL8_48_FH                                    |

## Full Example

Since auto discovery has not been added yet Things need to be defined manually. You need a `hdl:bridge` definition incl the right IP address of the HDL network item and its port number that should be 6000.

hdl.things:

```java
Bridge hdl:bridge:Setup [Ip="192.168.10.250", Port=6000]{
    Thing MRDA06 1020 [Subnet=1, DeviceID=20, refreshInterval=60]
    Thing MRDA06 1021 [Subnet=1, DeviceID=21]
    Thing MRDA06 1022 [Subnet=1, DeviceID=22]
    Thing MDT0601 1023 [Subnet=1, DeviceID=23, refreshInterval=60]
    Thing MDT0601 1024 [Subnet=1, DeviceID=24]
    Thing MDT04015 1025 [Subnet=1, DeviceID=25]
    Thing MR12xx 1030 [Subnet=1, DeviceID=30]
    Thing MR12xx 1031 [Subnet=1, DeviceID=31]
    Thing MR12xx 1032 [Subnet=1, DeviceID=32]
    Thing MR12xx 1033 [Subnet=1, DeviceID=33]
    Thing MR16xx 1034 [Subnet=1, DeviceID=34]
    Thing MR08xx 1035 [Subnet=1, DeviceID=35]
    Thing MR04xx 1036 [Subnet=1, DeviceID=36]
    Thing MW02 1038 [Subnet=1, DeviceID=38]
    Thing MS12 1040 [Subnet=1, DeviceID=40, refreshInterval=5]
    Thing MS12 1041 [Subnet=1, DeviceID=41, refreshInterval=5]
    Thing MS08 1050 [Subnet=1, DeviceID=50, refreshInterval=5]
    Thing MS08 1051 [Subnet=1, DeviceID=51, refreshInterval=5]
    Thing MPL8_48_FH 1070 [Subnet=1, DeviceID=70, refreshInterval=120]
    Thing MPL8_48_FH 1071 [Subnet=1, DeviceID=71, refreshInterval=120]
    Thing MFH06 1072 [Subnet=1, DeviceID=72, channelNumber=1, refreshInterval=120]
    Thing MPT04 1090 [Subnet=1, DeviceID=90]
    Thing MPT04 1093 [Subnet=1, DeviceID=93, refreshInterval=-1]
    Thing MS24 1100 [Subnet=1, DeviceID=100, refreshInterval=120]
    Thing ML01 1101 [Subnet=1, DeviceID=101] {
        Type UVSwitch : UVSwitch201 "Alarm Status" [ switchNumber=201 ]
    }
    Thing MPT04 1110 [Subnet=1, DeviceID=110]
}
```

hdl.items:

```java
Dimmer  E2R1LD01        "Roof lights [%d %%]"                                   {channel="hdl:MDT0601:Setup:1023:DimChannel6"}
Number  E2R1DLP01       "Temperature [%.1f °C]"             <temperature>       {channel="hdl:MPL8_48_FH:Setup:1082:temperature"}
Switch  E2R1ST01        "Sockets in room"                                       {channel="hdl:MR12xx:Setup:1032:RelayCh12"}
Dimmer  E2R2LD01        "Roof lights [%d %%]"                                   {channel="hdl:MDT0601:Setup:1024:DimChannel1"}
Switch  E2R2ST01        "Sockets in room"                                       {channel="hdl:MR12xx:Setup:1034:RelayCh5"}
Dimmer  E2R3LD01        "Roof lights [%d %%]"                                   {channel="hdl:MDT0601:Setup:1024:DimChannel2"}
Switch  E2R3ST01        "Sockets in room"                                       {channel="hdl:MR12xx:Setup:1034:RelayCh6"}
Contact E2R48i101C1     "8in1 DryContact1"                                      {channel="hdl:MS08:Setup:1050:DryContact1Status"}
Contact E2R48i101C2     "8in1 DryContact2"                                      {channel="hdl:MS08:Setup:1050:DryContact2Status"}
Number  E2R48i101Br     "8in1 Brightness"                   <sun>               {channel="hdl:MS08:Setup:1050:Brightness"}
Switch  E2R48i101Mo     "8in1 MotionSensor"                 <motion>            {channel="hdl:MS08:Setup:1050:MotionSensor"}
Number  E2R512i101      "Temperature [%.1f °C]"             <temperature>       {channel="hdl:MS12:Setup:1043:temperature"}
Contact E2R512i101C1    "12in1 DryContact1"                                     {channel="hdl:MS12:Setup:1043:DryContact1Status"}
Contact E2R512i101C2    "12in1 DryContact2"                                     {channel="hdl:MS12:Setup:1043:DryContact2Status"}
Switch  E2R512i101Mo    "12in1 MotionSensor [%s]"           <motion>            {channel="hdl:MS12:Setup:1043:MotionSensor"}
Switch  E2R512i101So    "12in1 Sonic [%s]"                  <motion>            {channel="hdl:MS12:Setup:1043:Sonic"}
Number  E2R512i101Br    "12in1 Brightness [%d Lux]"         <sun>               {channel="hdl:MS12:Setup:1043:Brightness"}
Number  E2R5DLP01       "Temperature [%.1f °C]"             <temperature>       {channel="hdl:MPL8_48_FH:Setup:1081:temperature"}
Number  E2R5DLP01CurTemp"Set Temperatur [%.1f °C]"          <temperature>       {channel="hdl:MPL8_48_FH:Setup:1081:FHCurrentTempSet"}
String  E2R5DLP01FHM    "Heat Mode: [%s]"                                       {channel="hdl:MPL8_48_FH:Setup:1081:FHMode"}
Rollershutter E2R5MW02  "Rollershutter [%s]"                                    {channel="hdl:MW02:Setup:1038:Shutter1Control"}
Switch  E2R5MPT04B1     "Panel Button 1"                                        {channel="hdl:MPT04:Setup:1093:Button1"}
Switch  E2R1UVAlarm     "Alarm Status"                                          {channel="hdl:ML01:Setup:1101:UVSwitch201"}
```
