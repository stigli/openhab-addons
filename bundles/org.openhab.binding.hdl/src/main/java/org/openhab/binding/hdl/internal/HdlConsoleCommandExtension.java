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
package org.openhab.binding.hdl.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.hdl.HdlBindingConstants;
import org.openhab.binding.hdl.internal.device.CommandType;
import org.openhab.binding.hdl.internal.handler.HdlBridgeHandler;
import org.openhab.binding.hdl.internal.handler.HdlPacket;
import org.openhab.core.io.console.Console;
import org.openhab.core.io.console.extensions.AbstractConsoleCommandExtension;
import org.openhab.core.io.console.extensions.ConsoleCommandExtension;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link HdlConsoleCommandExtension} class provides additional options through the console command
 * line, currently a "busstats" command that prints a snapshot of {@link HdlBridgeHandler#getBusStatistics()}
 * for every HDL bridge.
 *
 * @author stigla - Initial contribution
 */
@Component(service = ConsoleCommandExtension.class)
@NonNullByDefault
public class HdlConsoleCommandExtension extends AbstractConsoleCommandExtension {

    private static final String SUBCMD_BUSSTATS = "busstats";
    private static final String SUBCMD_CURTAINDURATION = "curtainduration";
    private static final String SUBCMD_DRYCONTACTPROBE = "drycontactprobe";
    private final ThingRegistry thingRegistry;

    @Activate
    public HdlConsoleCommandExtension(@Reference ThingRegistry thingRegistry) {
        super("hdl", "HDL Buspro binding commands.");
        this.thingRegistry = thingRegistry;
    }

    @Override
    public void execute(String[] args, Console console) {
        if (args.length > 0 && SUBCMD_BUSSTATS.equals(args[0])) {
            handleBusStats(console);
        } else if (args.length == 4 && SUBCMD_CURTAINDURATION.equals(args[0])) {
            handleCurtainDuration(console, args[1], args[2], args[3]);
        } else if (args.length == 4 && SUBCMD_DRYCONTACTPROBE.equals(args[0])) {
            handleDryContactProbe(console, args[1], args[2], args[3]);
        } else {
            printUsage(console);
        }
    }

    private void handleBusStats(Console console) {
        List<Thing> bridges = findBridges();
        if (bridges.isEmpty()) {
            console.println("No HDL bridge things found.");
            return;
        }
        for (Thing bridge : bridges) {
            if (bridge.getHandler() instanceof HdlBridgeHandler handler) {
                console.println(bridge.getUID().toString() + ":");
                console.println(handler.getBusStatistics().formatSummary());
            } else {
                console.println(bridge.getUID().toString() + ": no handler (not initialized)");
            }
        }
    }

    /**
     * Sends a {@link CommandType#Get_Curtain_Duration_Request} probe to the given Subnet/Device/channel and
     * returns - the response (if the device supports this command at all) arrives asynchronously through
     * the normal packet log, not this console session. Enable DEBUG logging on org.openhab.binding.hdl and
     * watch for CommandType Get_Curtain_Duration_Response to see what comes back, if anything.
     */
    private void handleCurtainDuration(Console console, String subnetArg, String deviceArg, String channelArg) {
        List<Thing> bridges = findBridges();
        if (bridges.isEmpty()) {
            console.println("No HDL bridge things found.");
            return;
        }
        if (!(bridges.get(0).getHandler() instanceof HdlBridgeHandler handler)) {
            console.println(bridges.get(0).getUID().toString() + ": no handler (not initialized)");
            return;
        }
        int subnet;
        int device;
        int channel;
        try {
            subnet = Integer.parseInt(subnetArg);
            device = Integer.parseInt(deviceArg);
            channel = Integer.parseInt(channelArg);
        } catch (NumberFormatException e) {
            console.println("Subnet, device and channel must all be numbers.");
            return;
        }
        HdlPacket p = new HdlPacket();
        p.setTargetSubnetID(subnet);
        p.setTargetDeviceId(device);
        p.setCommandType(CommandType.Get_Curtain_Duration_Request);
        p.setData(new byte[] { (byte) channel });
        try {
            handler.sendPacket(p);
            console.println("Sent Get_Curtain_Duration_Request to " + subnet + "." + device + ", channel " + channel
                    + " - check the DEBUG log for a Get_Curtain_Duration_Response (or nothing, if "
                    + "this device doesn't support the command).");
        } catch (IOException e) {
            console.println("Failed to send: " + e.getMessage());
        }
    }

    /**
     * Sends a single, isolated {@link CommandType#Read_Dry_Contact_Status} request for one channel - unlike
     * MS24's normal startup probe (24 requests, paced 50ms apart), this sends exactly one, with nothing else
     * from this binding going out at the same time. Added 2026-08-21 to isolate whether channels that never
     * respond during the full 24-channel burst (seen so far: 16, 17) are a burst-timing/position artifact or
     * genuinely fail even in complete isolation - run it repeatedly against a suspect channel and compare to
     * a known-good one.
     */
    private void handleDryContactProbe(Console console, String subnetArg, String deviceArg, String channelArg) {
        List<Thing> bridges = findBridges();
        if (bridges.isEmpty()) {
            console.println("No HDL bridge things found.");
            return;
        }
        if (!(bridges.get(0).getHandler() instanceof HdlBridgeHandler handler)) {
            console.println(bridges.get(0).getUID().toString() + ": no handler (not initialized)");
            return;
        }
        int subnet;
        int device;
        int channel;
        try {
            subnet = Integer.parseInt(subnetArg);
            device = Integer.parseInt(deviceArg);
            channel = Integer.parseInt(channelArg);
        } catch (NumberFormatException e) {
            console.println("Subnet, device and channel must all be numbers.");
            return;
        }
        HdlPacket p = new HdlPacket();
        p.setTargetSubnetID(subnet);
        p.setTargetDeviceId(device);
        p.setCommandType(CommandType.Read_Dry_Contact_Status);
        p.setData(new byte[] { (byte) 1, (byte) channel });
        try {
            handler.sendPacket(p);
            console.println("Sent Read_Dry_Contact_Status to " + subnet + "." + device + ", channel " + channel
                    + " - check the DEBUG log for a Response_Read_Dry_Contact_Status (or nothing).");
        } catch (IOException e) {
            console.println("Failed to send: " + e.getMessage());
        }
    }

    private List<Thing> findBridges() {
        List<Thing> bridges = new ArrayList<>();
        for (Thing thing : thingRegistry.getAll()) {
            if (HdlBindingConstants.THING_TYPE_BRIDGE.equals(thing.getThingTypeUID())) {
                bridges.add(thing);
            }
        }
        return bridges;
    }

    @Override
    public List<String> getUsages() {
        return Arrays.asList(
                buildCommandUsage(SUBCMD_BUSSTATS,
                        "Show HDL bus traffic statistics (message rate, top senders/receivers)"),
                buildCommandUsage(SUBCMD_CURTAINDURATION + " <subnet> <device> <channel>",
                        "Probe a curtain device for its configured travel duration (experimental, unconfirmed command)"),
                buildCommandUsage(SUBCMD_DRYCONTACTPROBE + " <subnet> <device> <channel>",
                        "Send a single isolated dry-contact status request for one channel (diagnostic)"));
    }
}
