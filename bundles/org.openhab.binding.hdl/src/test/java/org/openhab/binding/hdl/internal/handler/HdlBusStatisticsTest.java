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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.hdl.internal.device.DeviceType;
import org.openhab.binding.hdl.internal.handler.HdlBusStatistics.UnknownTarget;

/**
 * Tests for {@link HdlBusStatistics#isUnknownTarget}/{@link HdlBusStatistics#getUnknownTargets} - surfaces
 * bus addresses that have been sent a command but never proven to exist (never seen as a sender, never
 * configured as a Thing), so a wrong Subnet/DeviceID in the physical HDL Setup Tool can be found and fixed.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
class HdlBusStatisticsTest {

    private static void send(HdlBusStatistics stats, String sourceSubnetDevice, String targetSubnetDevice) {
        String[] sourceParts = sourceSubnetDevice.split("\\.", 2);
        String[] targetParts = targetSubnetDevice.split("\\.", 2);
        stats.recordPacket(Integer.parseInt(sourceParts[0]), Integer.parseInt(sourceParts[1]), DeviceType.MW02,
                Integer.parseInt(targetParts[0]), Integer.parseInt(targetParts[1]));
    }

    @Test
    void pureReceiverIsUnknown() {
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.12", "1.25");
        assertTrue(stats.isUnknownTarget("1.25", Set.of()));
    }

    @Test
    void addressThatHasAlsoSentIsNotUnknown() {
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.12", "1.25");
        send(stats, "1.25", "1.12"); // 1.25 answers - now proven to exist
        assertFalse(stats.isUnknownTarget("1.25", Set.of()));
    }

    @Test
    void broadcastAndGatewayAreNeverUnknown() {
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.12", "255.255");
        send(stats, "1.12", "1.254");
        assertFalse(stats.isUnknownTarget("255.255", Set.of()));
        assertFalse(stats.isUnknownTarget("1.254", Set.of()));
    }

    @Test
    void subnetScopedBroadcastIsNeverUnknown() {
        // Regression for a real false positive (2026-09-08): Broadcast_Temperature and presumably every
        // other Broadcast_* command targets "<own subnet>.255", not just the full 255.255 bus-wide
        // broadcast - confirmed via real hardware, 13 distinct senders all targeting 1.255.
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.74", "1.255");
        send(stats, "2.5", "2.255");
        assertFalse(stats.isUnknownTarget("1.255", Set.of()));
        assertFalse(stats.isUnknownTarget("2.255", Set.of()));
        assertEquals(List.of(), stats.getUnknownTargets(Set.of()));
    }

    @Test
    void setupToolAddressIsNeverUnknown() {
        // Regression for a real false positive (2026-09-08): three unrelated real devices (1.43, 1.92,
        // 1.95) all sent legitimate response traffic targeting exactly 240.254 while stigla was actively
        // editing each of them in the physical HDL Setup Tool - empirically the tool's own self-address,
        // analogous to this binding's own GATEWAY_ADDRESS.
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.43", "240.254");
        send(stats, "1.92", "240.254");
        send(stats, "1.95", "240.254");
        assertFalse(stats.isUnknownTarget("240.254", Set.of()));
        assertEquals(List.of(), stats.getUnknownTargets(Set.of()));
    }

    @Test
    void configuredAddressIsNeverUnknownEvenIfNeverSeenAsSender() {
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.12", "1.25");
        assertFalse(stats.isUnknownTarget("1.25", Set.of("1.25")));
    }

    @Test
    void unknownTargetListsEveryDistinctSender() {
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.12", "1.25");
        send(stats, "1.13", "1.25");
        send(stats, "1.12", "1.25"); // repeat sender - must not duplicate

        List<UnknownTarget> unknown = stats.getUnknownTargets(Set.of());
        assertEquals(1, unknown.size());
        UnknownTarget target = unknown.get(0);
        assertEquals("1.25", target.address());
        assertEquals(List.of("1.12", "1.13"), target.senderAddresses());
    }

    @Test
    void unknownTargetsSortNumericallyNotLexicographically() {
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.1", "1.10");
        send(stats, "1.1", "1.2");

        List<UnknownTarget> unknown = stats.getUnknownTargets(Set.of());
        assertEquals(List.of("1.2", "1.10"), unknown.stream().map(UnknownTarget::address).toList());
    }

    @Test
    void noUnknownTargetsYieldsEmptyList() {
        HdlBusStatistics stats = new HdlBusStatistics();
        send(stats, "1.12", "1.13");
        send(stats, "1.13", "1.12");
        assertEquals(List.of(), stats.getUnknownTargets(Set.of()));
    }
}
