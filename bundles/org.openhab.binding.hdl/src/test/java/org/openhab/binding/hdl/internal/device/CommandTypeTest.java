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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * {@link CommandType#create(int)} and {@link CommandType#toString()} are two independently hand-maintained
 * switch statements over the same numeric codes - this binding has already had real bugs where one was
 * updated and the other wasn't (see the "Fix incorrect command codes in CommandType" commit history). This
 * test walks every declared enum constant and confirms both switches agree with it and with each other, so
 * that class of regression fails a build instead of silently mis-tagging bus traffic.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
class CommandTypeTest {

    /**
     * {@code Response_Modify_Floor_Heating_Day_Night_Time_Setting} deliberately doesn't round-trip - see the
     * FIXME comment directly above its declaration in {@link CommandType}. The HDL Buspro spec itself reuses
     * 0x1D1F/7455 for two different responses; the real, distinct code for this one is unknown, so
     * {@code create()}/{@code toString()} intentionally resolve 7455 to
     * {@code Response_Read_Floor_Heating_Day_Night_Time_Setting} instead. Not a bug to fix here.
     */
    @Test
    void everyDeclaredValueRoundTripsThroughCreateAndToString() {
        for (CommandType type : CommandType.values()) {
            if (type == CommandType.Invalid
                    || type == CommandType.Response_Modify_Floor_Heating_Day_Night_Time_Setting) {
                continue;
            }
            int value = type.getValue();

            assertEquals(type, CommandType.create(value),
                    "CommandType.create(" + value + ") should resolve back to " + type.name());
            assertEquals(type.name(), type.toString(),
                    "toString() for " + type.name() + " (value " + value + ") doesn't match its own enum name");
        }
    }

    @Test
    void unknownValueResolvesToInvalid() {
        assertEquals(CommandType.Invalid, CommandType.create(-1));
        assertNotEquals(CommandType.Invalid, CommandType.create(CommandType.Scene_Control.getValue()));
    }
}
