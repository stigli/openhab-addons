# HDL Binding

This is the binding for HDL.
This binding allows you to integrate, view and control the HDL  items in the openHAB environment

## Supported Things

This binding supports for now 15 different physical HDL devices, plus a virtual `Scene` Thing for triggering
scenes (see "HDL Scenes" below). More will be added as the binding are expanded.
Thing names for physical devices use the article number that HDL are using.

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
| Scene         | Thing     | Virtual - triggers an existing scene defined in the HDL Setup Tool; not a physical device, so it isn't discovered (see "HDL Scenes" below) |
| AC            | Thing     | Virtual, send-only - controls a dedicated HDL AC gateway device; not discovered, not confirmed on real hardware (see "AC Control" below) |

## Discovery

The bridge actively searches the HDL bus for devices once when it comes online, and again whenever a manual
Inbox scan is triggered; devices found this way, as well as any device that is seen sending traffic for other
reasons, show up in the Inbox. Not every discovered device type maps to a supported Thing type yet (see
`HdlDeviceDiscoveryService`). Confirmed working on real hardware.

## Device Firmware/Revision Codes

Every physical HDL device reports a numeric product code on the bus (`DeviceType.java`), which this binding
maps to a Thing type. HDL has released most product lines under several different codes (amp ratings,
hardware revisions); an unrecognized code is silently dropped rather than routed to its Thing. Full list of
codes each Thing type currently recognizes:

| Thing      | Codes covered                                              |
|------------|--------------------------------------------------------------|
| MDT0601    | 608, 621                                                    |
| MDT04015   | 620                                                          |
| ML01       | 1100, 1101, 1102, 1103, 1105, 1106, 1107, 1108, 1109, 1110  |
| MPL8_48_FH | 158, 162, 167, 180, 186                                     |
| MFH06      | 209, 210, 211                                               |
| MPT04      | 226                                                          |
| MR16xx     | 450, 451, 453                                               |
| MR12xx     | 11, 19, 22, 150, 429, 430, 431, 440, 443, 446, 449, 452     |
| MR08xx     | 23, 427, 428, 436, 439, 442, 445, 448                       |
| MR04xx     | 153, 423, 424, 433, 434, 435, 437, 438, 441, 444, 447       |
| MRDA06     | 17, 36, 46, 454, 662                                        |
| MS08       | 305, 309, 314, 315, 316, 318, 322, 329                      |
| MS12       | 308, 321                                                    |
| MS24       | 141, 142, 352, 353, 358                                     |
| MW02       | 704, 705, 706, 707                                          |

**Not merged**: `MW02` codes 700-703 and `MS12` codes 92/140 have a structurally different description in
HDL's own code table (not just an amp-rating/revision suffix) from what's covered above, so they're left
unmapped pending a real capture rather than assumed interchangeable. Adding a missing code is a one-line
addition to `Device.java`'s `create(DeviceConfiguration)` switch.

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

## Curtain Position (MW02)

`MW02`'s `Shutter1Control`/`Shutter2Control` channels only support `UP`/`DOWN`/`STOP` - there's no native
percentage feedback, so a Rollershutter item bound directly to them jumps straight to 0%/100% the instant a
move starts, instead of tracking real travel over time.

openHAB core already ships a purpose-built fix for exactly this class of problem (motors with no position
feedback, e.g. Somfy): the `org.openhab.transform.rollershutterposition` add-on's `ROLLERSHUTTERPOSITION`
profile. Apply it on the Item link instead of writing any binding-specific code:

```java
Rollershutter E2R5MW02 "Rollershutter [%s]" {channel="hdl:MW02:Setup:1038:Shutter1Control"[profile="transform:ROLLERSHUTTERPOSITION", uptime=35, downtime=35]}
```

`uptime`/`downtime` are the full-travel time in seconds. Don't guess these - `MW02` queries the device's own
configured travel time automatically at startup and exposes it on the read-only `Curtain1Duration`/
`Curtain2Duration` channels; check those (e.g. link them to a temporary `Number` item, or view the Thing's
channel state in MainUI) and copy the value straight into `uptime`/`downtime` above. Confirmed working
against real MW02 hardware (2026-08-21, both channels returned `35`), though the specific unit (assumed
seconds) isn't formally documented anywhere, just inferred from a plausible value.

If a channel stays undefined, the device didn't respond to the query - you can retry it manually via the
console, watching the log for a response:

```text
openhab:hdl curtainduration <subnet> <device> <channel>
```

(enable DEBUG logging for `org.openhab.binding.hdl` first, and look for `Get_Curtain_Duration_Response`).

## HDL Scenes

### Automatic sync (no configuration needed)

If a scene configured in the HDL Setup Tool changes a dimmer/relay's channels (whether triggered from a
physical panel, another scene, a `Scene` Thing below, or anywhere else on the bus), the affected
`DimChannel`/`RelayCh` channels on `MDT0601`, `MDT04015`, `MRDA06`, `MR16xx`, `MR12xx`, `MR08xx`, and
`MR04xx` update automatically to reflect the new state - no separate "Scene" Thing or configuration needed
for this direction. Based on the HDL Buspro `Broadcast_Status_of_Scene` message. Confirmed working on real
hardware.

### Triggering a scene from openHAB

Use a `Scene` Thing to fire an existing scene (defined once in the HDL Setup Tool) from openHAB:

```java
Thing Scene 1032_5_3 [Subnet=1, DeviceID=32, area=5, scene=3]
```

- `Subnet`/`DeviceID` - any device on the bus that's a member of the target area (commonly one of the
  relay/dimmer devices affected by the scene) - scenes don't have their own bus address.
- `area` - the area number the scene is configured under in the HDL Setup Tool.
- `scene` - the scene number to run.

It has one channel, `Trigger` (Switch): sending `ON` fires the scene. `OFF` does nothing - scenes are a
fire-once action, not a stateful switch.

```java
Switch E2Scene1 "Evening Scene" {channel="hdl:Scene:Setup:1032_5_3:Trigger"}
```

Confirmed working end-to-end on real hardware (2026-08-22): the outbound `Scene_Control` command and the
target device's resulting physical effect both matched exactly what was expected.

## AC Control

**⚠️ Not confirmed on real hardware - no AC gateway was available to test against.** Implemented purely from
the official "HDL-BUS Pro operation codes" reference document (2026-08-22). Use at your own risk, and check
carefully that the AC unit actually does what you expect before relying on it.

This is a **different thing from the `ACMode`/`ACFanSpeed`/`AC*TempSet` channels on `MPL8_48_FH`** - those
are read-only and reflect what a DLP panel's own AC page is showing (see "Channels" below). The `AC` Thing
here targets a **separate, dedicated AC gateway device** (HDL sells one under the name "CoolBox VRV
Gateway") that manages one or more physical AC units, addressed by an `acNumber` (1-128) rather than by
Subnet/DeviceID alone - one gateway can control multiple AC units.

**This Thing is send-only.** The gateway that would answer these commands reports a device type this binding
doesn't recognize (no real hardware was available to capture it from), so status can never be read back -
every channel is write-only, with no confirmed state, and the Thing will not show real AC status anywhere.
If you have this hardware and can capture its device type from the log (look for `Unhandled`/`Invalid`
device type entries after sending a command), that's the one piece needed to add read support - it's a
one-line addition to `DeviceType.java`, same as previous device compatibility fixes in this binding.

```java
Thing AC 1200 [Subnet=1, DeviceID=200, acNumber=1, temperatureType="C"]
```

```java
Switch  E2ACPower       "AC Power"          {channel="hdl:AC:Setup:1200:Power"}
String  E2ACMode        "AC Mode"           {channel="hdl:AC:Setup:1200:Mode"}
String  E2ACFanSpeed    "AC Fan Speed"      {channel="hdl:AC:Setup:1200:FanSpeed"}
Number:Temperature E2ACCoolSet "AC Cooling Setpoint [%.1f %unit%]" {channel="hdl:AC:Setup:1200:CoolingSetpoint"}
```

`Mode` accepts `Cooling`/`Heating`/`Fan`/`Auto`/`Dry`. `FanSpeed` accepts `Auto`/`High`/`Medium`/`Low`. Since
there's no status readback, this binding tracks your last-sent values internally and re-sends all of them
together whenever any one channel changes (the underlying `Control_AC` command requires the full state in
every packet, not just the field that changed) - so the first command you send after a restart will use this
binding's built-in defaults (Auto/Auto/22°C) for anything you haven't explicitly set yet, not whatever the
AC unit's own actual last real state was.

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

`MS24` only queries the dry-contact channels that are actually linked to an Item - if you're only using 2 of
the 24, only those 2 get polled, not all 24. For whichever channels are linked, firing all their requests at
once at startup turned out to be unreliable on real hardware - it competes with every other Thing's own
startup requests for the shared bus. The binding handles this with an initial 6-second delay before the very
first probe (letting the rest of the install's startup burst clear), 150ms pacing between requests, and
automatic retry for any channel that didn't respond - confirmed reliable across repeated full-restart tests.
No configuration needed for any of this, it's automatic; only the one-time startup probe is affected, not
periodic `refreshInterval` polls.

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
| Shutter(1-2)Control           | Rollershutter    | Device control: send UP/DOWN/STOP commands; state reflects the last known UP/DOWN direction (no native percentage support - see "Curtain Position (MW02)" above for a fix). | MW02                                          |
| Curtain(1-2)Duration           | Number           | Configured full-travel duration in seconds, queried from the device automatically at startup. Read-only. See "Curtain Position (MW02)" above.  | MW02                                          |
| FHMode                        | String           | Floor heating mode (Normal, Day, Night, Away, Timer). Writable on MPL8_48_FH.         | MPL8_48_FH, MFH06                             |
| FHNormalTempSet / FHTempSet / FHNightTempSet / FHAwayTempSet | Number:Temperature | Floor heating setpoint temperatures. Individually writable on MPL8_48_FH.       | MPL8_48_FH, MFH06                             |
| FHCurrentTempSet              | Number:Temperature | Current floor heating temperature.                            | MPL8_48_FH, MFH06                             |
| FHTemperaturType              | String           | Temperature unit (C or F) the panel is reporting setpoints in.  | MPL8_48_FH                                    |
| FHTimer                       | String           | Active schedule period (Day or Night); only meaningful when FHMode is Timer.  | MPL8_48_FH                                    |
| ACMode                        | String           | AC mode (Cooling, Heating, Fan, Auto, Dehumidfy). Read-only - view only, HDL doesn't expose a documented way to control AC via the DLP panel yet.  | MPL8_48_FH                                    |
| ACFanSpeed                    | String           | AC fan speed (Auto, High, Medium, Low). Read-only, see ACMode.                  | MPL8_48_FH                                    |
| ACCoolingTempSet / ACHeatTempSet / ACAutoTempSet / ACDryTempSet | Number:Temperature | AC setpoint temperatures. Read-only, see ACMode.        | MPL8_48_FH                                    |
| ACCurrentTempSet              | Number:Temperature | Current AC temperature.                                        | MPL8_48_FH                                    |
| MusicCommand                  | String           | Raw command the panel's Music tab sends its onboard Z-Audio engine (see "Music Tab / Z-Audio" below). Read-only.  | MPL8_48_FH                                    |
| ACPower                       | Switch           | AC on/off, as shown on the DLP panel's own AC page. Read-only. Not confirmed on real hardware (see "AC Control" above for actually controlling AC). | MPL8_48_FH |
| PanelKeyLock / LockAC / SetupPageLock | Switch    | Panel/AC/setup-page key-lock status. Read-only, not confirmed on real hardware. | MPL8_48_FH |
| LCDBacklightStatus            | Switch           | Whether the panel's LCD backlight feature is on. Read-only, not confirmed on real hardware. | MPL8_48_FH |
| PanelBacklight / StatusLight  | Number           | Panel backlight / status light brightness (0-100). Read-only, not confirmed on real hardware. | MPL8_48_FH |

## Music Tab / Z-Audio

`MPL8_48_FH` panels have a "Music" tab in the HDL Setup Tool (SD Card/FTP/Radio/Audio IN, Play/Stop). It looks
like a normal set of configurable buttons - each row lets you set a `Type` (Scene / Universal Switch / Single
Channel Control) and target Subnet/Device, same as any other panel button - but **that configuration is
inert for Play/Stop specifically**, confirmed on real hardware (2026-08-18) across multiple test rounds with
otherwise valid, distinct, saved configurations. Pressing Play/Stop never sends the configured command at
all; instead the panel always drives its own onboard Z-Audio engine directly with a plain-text serial-style
command, `*Z<zone><command>\r` (e.g. `*Z1ON` for Play, `*Z1STATUS?` as a repeating ~1-2s background poll
while the tab is open). This looks like a firmware-level restriction on this specific button type, not a
configuration mistake - the Universal Switch/Scene/Single-Channel-Control options in that CMD list appear to
be vestigial UI here.

The `MusicCommand` channel exposes whatever command comes through verbatim (`STATUS?` filtered out as pure
polling noise), so you can react to it in a rule - but in practice only `ON` (tied to a Play press) has ever
been observed; **Stop never produces any distinguishable command at all**, on any test so far, regardless of
CMD list configuration. This channel updates on every press, including repeats of the same value, so use a
"received update" rule trigger rather than "changed" if you need every press to register.

If you need a "stop" action, this specific button isn't a reliable way to get it - a Universal Switch
elsewhere on the panel (a button not under the Music tab) is the better-supported route, since that
mechanism is confirmed working elsewhere in this binding (see "Universal Switches (UVSwitch)" above).

## Full Example

While discovered devices show up in the Inbox automatically, defining Things manually (as below) gives you
full control over config like `refreshInterval` up front. You need a `hdl:bridge` definition incl the right IP address of the HDL network item and its port number that should be 6000.

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
    Thing Scene 1032_5_3 [Subnet=1, DeviceID=32, area=5, scene=3]
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
Switch  E2Scene1        "Evening Scene"                                         {channel="hdl:Scene:Setup:1032_5_3:Trigger"}
```
