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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.hdl.internal.handler.HdlPacket;

/**
 * Tests for {@link ML01}'s {@code Broadcast_System_Date_and_Time_Every_Minute} parsing - guards two
 * historical bugs: using (years since 2000) + 2100 instead of +100 in {@link Date}'s deprecated
 * year-offset constructor, and passing the wire's 1-12 month directly instead of converting to the
 * constructor's expected 0-11.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("deprecation")
class ML01Test {

    private static ML01 newDevice() {
        DeviceConfiguration config = DeviceConfiguration.create("1", 1, 1, DeviceType.ML01);
        return new ML01(config);
    }

    @Test
    void parsesYearMonthDayTimeFromWireBytes() {
        ML01 device = newDevice();
        HdlPacket p = new HdlPacket();
        p.commandType = CommandType.Broadcast_System_Date_and_Time_Every_Minute;
        // years-since-2000, month(1-12), day, hour, minute, second - 2026-08-28 14:30:05.
        p.data = new byte[] { 26, 8, 28, 14, 30, 5 };
        device.treatHDLPacketForDevice(p);

        Date result = device.getDateSetpoint();
        assertNotNull(result);
        assertEquals(126, result.getYear(), "years-since-2000 must map to +100, not +2100, in Date's offset");
        assertEquals(7, result.getMonth(), "wire month is 1-12 but Date's constructor expects 0-11");
        assertEquals(28, result.getDate());
        assertEquals(14, result.getHours());
        assertEquals(30, result.getMinutes());
        assertEquals(5, result.getSeconds());
    }

    @Test
    void parsesJanuaryWithoutGoingNegative() {
        ML01 device = newDevice();
        HdlPacket p = new HdlPacket();
        p.commandType = CommandType.Broadcast_System_Date_and_Time_Every_Minute;
        // Month = January (1) is the edge case where a missing -1 conversion would be most visible.
        p.data = new byte[] { 26, 1, 1, 0, 0, 0 };
        device.treatHDLPacketForDevice(p);

        Date result = device.getDateSetpoint();
        assertNotNull(result);
        assertEquals(0, result.getMonth(), "January must map to month index 0, not stay at 1 or wrap negative");
    }
}
