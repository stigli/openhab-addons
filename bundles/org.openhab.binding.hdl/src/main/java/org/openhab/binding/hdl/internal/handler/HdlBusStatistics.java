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
import java.util.List;
import java.util.Map;
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
    private static final String BROADCAST_ADDRESS = "255.255";

    private final Instant startTime = Instant.now();
    private final AtomicLong totalMessages = new AtomicLong();
    private final AtomicLong invalidPackets = new AtomicLong();

    private final Map<String, LongAdder> senderCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> receiverCounts = new ConcurrentHashMap<>();
    private final Map<String, String> addressLabels = new ConcurrentHashMap<>();

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

    private List<Map.Entry<String, Long>> topEntries(Map<String, LongAdder> counts) {
        return counts.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue().sum()))
                .sorted(Map.Entry.<String, Long> comparingByValue().reversed()).limit(TOP_N)
                .collect(Collectors.toList());
    }

    private String labelFor(String address) {
        if (BROADCAST_ADDRESS.equals(address)) {
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
     */
    public synchronized String formatSummary() {
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

        return sb.toString();
    }
}
