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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.hdl.HdlBindingConstants;
import org.openhab.binding.hdl.internal.handler.HdlBridgeHandler;
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
        return Arrays.asList(buildCommandUsage(SUBCMD_BUSSTATS,
                "Show HDL bus traffic statistics (message rate, top senders/receivers)"));
    }
}
