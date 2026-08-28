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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Guards the numbered {@code CHANNEL_*} family constants (DryContact/RelayCh/DimChannel/Button) against the
 * historical typo class of bug where the string value drifts from what the field name promises - e.g. a
 * past {@code CHANNEL_DRYCONTACT10 = "DryContac10Status"} typo. Walks every declared field via reflection
 * rather than hardcoding each one, so newly added channels are covered automatically.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
class HdlBindingConstantsTest {

    private static final Pattern NUMBERED_FIELD = Pattern
            .compile("^CHANNEL_(DRYCONTACT|RELAYCH|DIMCHANNEL|BUTTON)(\\d+)$");

    @Test
    void everyNumberedChannelConstantMatchesItsFieldName() throws IllegalAccessException {
        int checked = 0;
        for (Field field : HdlBindingConstants.class.getFields()) {
            Matcher matcher = NUMBERED_FIELD.matcher(field.getName());
            if (!matcher.matches() || field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String family = matcher.group(1);
            String number = matcher.group(2);
            String expected = switch (family) {
                case "DRYCONTACT" -> "DryContact" + number + "Status";
                case "RELAYCH" -> "RelayCh" + number;
                case "DIMCHANNEL" -> "DimChannel" + number;
                case "BUTTON" -> "Button" + number;
                default -> throw new IllegalStateException("Unhandled family: " + family);
            };
            assertEquals(expected, field.get(null), "field " + field.getName() + " has drifted from its name");
            checked++;
        }
        assertTrue(checked > 0, "no CHANNEL_* fields matched - the naming pattern or class moved");
    }

    @Test
    void patternRejectsUnrelatedFieldNames() {
        assertFalse(NUMBERED_FIELD.matcher("CHANNEL_TEMPERATURE").matches());
        assertFalse(NUMBERED_FIELD.matcher("CHANNEL_DRYCONTACT").matches());
    }
}
