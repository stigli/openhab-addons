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

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.hdl.internal.device.DeviceType;

/**
 * Tracks bus traffic statistics (message rate, invalid packet count, per-address message counts), fed from
 * every packet observed in {@link HdlBridgeHandler#onRead}. Used both by the "busstats" console command
 * (on-demand snapshot) and the bridge's BusMessageRate/BusInvalidPacketCount channels (updated
 * periodically).
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public class HdlBusStatistics {

    private static final int TOP_N = 5;

    /**
     * openHAB's own outbound source address, unconditionally set by {@link HdlBridgeHandler#sendPacket} -
     * every real device's response to an openHAB-initiated query targets this address, but the binding's
     * own outbound sends are never looped back through {@link HdlBridgeHandler#onRead}, so this would
     * otherwise be a permanent false positive in {@link #isUnknownTarget}.
     */
    private static final String GATEWAY_ADDRESS = "1.254";

    /**
     * Empirically observed (2026-09-08, not from official documentation) to be the physical HDL Setup Tool
     * PC software's own self-address, analogous to {@link #GATEWAY_ADDRESS} for this binding: three
     * unrelated real devices (1.43, 1.92, 1.95) all sent legitimate response traffic
     * (Response_Read_Sensors_Status, Response_Read_Status_of_Channels, Response_Read_Status_of_UV_Switch)
     * targeting exactly {@code 240.254}, in bursts that lined up exactly with stigla actively editing each
     * of those specific devices in the Setup Tool, stopping when he moved to the next one. Corroborated by
     * an earlier, separate observation (2026-08-22) of unexplained traffic targeting Subnet 240 from a
     * different device (MS24). Kept as a single specific address (not generalized to "any subnet, device
     * 254" the way {@link #isBroadcastAddress} generalizes device 255) since there's no evidence of the
     * Setup Tool using any subnet other than 240.
     */
    private static final String SETUP_TOOL_ADDRESS = "240.254";

    private final Instant startTime = Instant.now();
    private final AtomicLong totalMessages = new AtomicLong();
    private final AtomicLong invalidPackets = new AtomicLong();

    private final Map<String, LongAdder> senderCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> receiverCounts = new ConcurrentHashMap<>();
    private final Map<String, String> addressLabels = new ConcurrentHashMap<>();

    /** Target address -> set of sender addresses that have targeted it, fed from {@link #recordPacket}. **/
    private final Map<String, Set<String>> targetSenders = new ConcurrentHashMap<>();

    private final AtomicLong currentSecondEpoch = new AtomicLong(-1);
    private final AtomicLong currentSecondCount = new AtomicLong();
    private final AtomicLong peakMessagesPerSecond = new AtomicLong();

    private volatile Instant lastSnapshotTime = startTime;
    private volatile long lastSnapshotTotal;

    public void recordInvalidPacket() {
        invalidPackets.incrementAndGet();
    }

    public void recordPacket(int sourceSubnetID, int sourceDeviceID, DeviceType sourceDeviceType, int targetSubnetID,
            int targetDeviceID) {
        totalMessages.incrementAndGet();

        String sender = sourceSubnetID + "." + sourceDeviceID;
        senderCounts.computeIfAbsent(sender, k -> new LongAdder()).increment();
        if (sourceDeviceType != DeviceType.Invalid) {
            addressLabels.put(sender, sourceDeviceType.toString());
        }

        String receiver = targetSubnetID + "." + targetDeviceID;
        receiverCounts.computeIfAbsent(receiver, k -> new LongAdder()).increment();
        targetSenders.computeIfAbsent(receiver, k -> ConcurrentHashMap.newKeySet()).add(sender);

        updateRate();
    }

    private void updateRate() {
        long nowSecond = Instant.now().getEpochSecond();
        long previousSecond = currentSecondEpoch.getAndSet(nowSecond);
        if (previousSecond == nowSecond) {
            currentSecondCount.incrementAndGet();
        } else {
            long countInPreviousSecond = currentSecondCount.getAndSet(1);
            peakMessagesPerSecond.updateAndGet(peak -> Math.max(peak, countInPreviousSecond));
        }
    }

    /**
     * Average messages/second since the last time a snapshot was taken (either via this method or
     * {@link #formatSummary()}), and resets that window. Used for the periodically-updated
     * BusMessageRate channel, so each channel update reflects only the period since the previous update.
     */
    public double takeRecentAverageRatePerSecond() {
        Instant now = Instant.now();
        long total = totalMessages.get();
        double seconds = Duration.between(lastSnapshotTime, now).toMillis() / 1000.0;
        long delta = total - lastSnapshotTotal;
        lastSnapshotTime = now;
        lastSnapshotTotal = total;
        return seconds > 0 ? delta / seconds : 0.0;
    }

    public long getInvalidPacketCount() {
        return invalidPackets.get();
    }

    public long getTotalMessageCount() {
        return totalMessages.get();
    }

    public double getAverageRatePerSecondSinceStart() {
        double seconds = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
        return seconds > 0 ? totalMessages.get() / seconds : 0.0;
    }

    public long getPeakMessagesPerSecond() {
        return Math.max(peakMessagesPerSecond.get(), currentSecondCount.get());
    }

    public Duration getUptime() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * A bus address that has been targeted by a command but never proven to exist and never configured as
     * a Thing - see {@link #isUnknownTarget} - together with every sender address observed targeting it, so
     * the specific offending device/panel can be found and its Setup Tool configuration fixed.
     */
    public record UnknownTarget(String address, List<String> senderAddresses) {
    }

    /**
     * Numeric (subnet, device) comparison for {@code "subnet.device"} address strings - plain string
     * ordering would sort {@code "10.5"} before {@code "2.5"}, wrong for a human-facing list.
     */
    private static int compareAddresses(String a, String b) {
        String[] partsA = a.split("\\.", 2);
        String[] partsB = b.split("\\.", 2);
        int subnetCompare = Integer.compare(Integer.parseInt(partsA[0]), Integer.parseInt(partsB[0]));
        if (subnetCompare != 0) {
            return subnetCompare;
        }
        return Integer.compare(Integer.parseInt(partsA[1]), Integer.parseInt(partsB[1]));
    }

    /**
     * True if {@code address}'s device portion is 255 - the HDL Buspro subnet-broadcast marker. Confirmed
     * on real hardware (2026-09-08): {@code Broadcast_Temperature} (and presumably every other
     * {@code Broadcast_*} command) is sent to {@code "<own subnet>.255"}, not just the full {@code 255.255}
     * bus-wide broadcast this binding's own {@link HdlBridgeHandler#sendDiscoverDeviceBroadcast} uses - 13
     * distinct real devices were observed doing this, ruling out a one-off Setup Tool misconfiguration. A
     * broadcast has no single answering device by definition, so it must never be treated as a candidate
     * unknown target - this also subsumes the {@code 255.255} case (subnet portion doesn't matter here).
     */
    private static boolean isBroadcastAddress(String address) {
        int dotIndex = address.indexOf('.');
        return dotIndex >= 0 && "255".equals(address.substring(dotIndex + 1));
    }

    /**
     * True if {@code address} has been targeted by a command but never once proven to exist: not a
     * broadcast address, not the gateway or Setup Tool address, never seen as a sender, and not present in
     * the caller-supplied {@code configuredAddresses} (Subnet/DeviceID pairs stigla has actually configured
     * as Things, bus traffic or not - see {@link HdlBridgeHandler#getConfiguredDeviceAddresses}). The one
     * canonical definition of "unknown", shared by {@link #getUnknownTargets} and
     * {@link HdlBridgeHandler}'s real-time per-packet log line, so the two can't silently diverge.
     * <p>
     * Note: {@link #senderCounts} is not gated by {@code sourceDeviceType != DeviceType.Invalid} the way
     * {@link #addressLabels} is (see {@link #recordPacket}) - an unrecognized product code still counts as
     * "proven to exist" here, which is correct for this feature (existence, not "is a known device type").
     */
    public boolean isUnknownTarget(String address, Set<String> configuredAddresses) {
        return !isBroadcastAddress(address) && !GATEWAY_ADDRESS.equals(address) && !SETUP_TOOL_ADDRESS.equals(address)
                && !senderCounts.containsKey(address) && !configuredAddresses.contains(address);
    }

    /**
     * Every bus address addressed by a command but never proven to exist - see {@link #isUnknownTarget} -
     * sorted by address, each with the sender(s) that targeted it (see {@link #targetSenders}).
     */
    public List<UnknownTarget> getUnknownTargets(Set<String> configuredAddresses) {
        return receiverCounts.keySet().stream().filter(address -> isUnknownTarget(address, configuredAddresses))
                .map(address -> new UnknownTarget(address,
                        targetSenders.getOrDefault(address, Set.of()).stream()
                                .sorted(HdlBusStatistics::compareAddresses).collect(Collectors.toList())))
                .sorted(Comparator.comparing(UnknownTarget::address, HdlBusStatistics::compareAddresses))
                .collect(Collectors.toList());
    }

    private List<Map.Entry<String, Long>> topEntries(Map<String, LongAdder> counts) {
        return counts.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue().sum()))
                .sorted(Map.Entry.<String, Long> comparingByValue().reversed()).limit(TOP_N)
                .collect(Collectors.toList());
    }

    private String labelFor(String address) {
        if (isBroadcastAddress(address)) {
            return "broadcast";
        }
        String label = addressLabels.get(address);
        return label != null ? label : "unknown";
    }

    private static String formatDuration(Duration d) {
        long totalSeconds = d.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh%dm%ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm%ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    /**
     * Renders a full human-readable snapshot, used by the "busstats" console command. Does not reset the
     * BusMessageRate channel's recent-average window (see {@link #takeRecentAverageRatePerSecond()}).
     *
     * @param configuredAddresses Subnet/DeviceID pairs configured as Things - see {@link #isUnknownTarget}.
     */
    public synchronized String formatSummary(Set<String> configuredAddresses) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("HDL bus stats (running %s):%n", formatDuration(getUptime())));
        sb.append(String.format("  Rate: %.1f msg/s (avg), %d msg/s (peak)%n", getAverageRatePerSecondSinceStart(),
                getPeakMessagesPerSecond()));
        sb.append(String.format("  Total: %,d messages, %,d invalid/unparseable%n", getTotalMessageCount(),
                getInvalidPacketCount()));

        sb.append(System.lineSeparator()).append("  Top ").append(TOP_N).append(" senders:")
                .append(System.lineSeparator());
        int rank = 1;
        for (Map.Entry<String, Long> entry : topEntries(senderCounts)) {
            sb.append(String.format("    %d. %-10s (%-10s) %,10d msgs%n", rank++, entry.getKey(),
                    labelFor(entry.getKey()), entry.getValue()));
        }

        sb.append(System.lineSeparator()).append("  Top ").append(TOP_N).append(" receivers:")
                .append(System.lineSeparator());
        rank = 1;
        for (Map.Entry<String, Long> entry : topEntries(receiverCounts)) {
            sb.append(String.format("    %d. %-10s (%-10s) %,10d msgs%n", rank++, entry.getKey(),
                    labelFor(entry.getKey()), entry.getValue()));
        }

        List<UnknownTarget> unknownTargets = getUnknownTargets(configuredAddresses);
        sb.append(System.lineSeparator())
                .append("  Unknown targets (addressed but never seen as a sender or configured as a Thing):")
                .append(System.lineSeparator());
        if (unknownTargets.isEmpty()) {
            sb.append("    none").append(System.lineSeparator());
        } else {
            for (UnknownTarget target : unknownTargets) {
                String senders = target.senderAddresses().stream().map(sender -> sender + " (" + labelFor(sender) + ")")
                        .collect(Collectors.joining(", "));
                sb.append(String.format("    %-10s <- %s%n", target.address(), senders));
            }
        }

        return sb.toString();
    }
}
