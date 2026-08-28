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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * {@link DeviceType#create(int)} and {@link DeviceType#toString()} are two independently hand-maintained
 * switch statements over ~670 numeric product codes - several codes are deliberately aliased to the same
 * enum constant (different firmware/hardware revisions of the same product, see the README's "Device
 * Firmware/Revision Codes" section), so this only checks each constant's own declared value, not every
 * alias it might also accept - but that's still enough to catch a constant whose primary case was left out
 * of one switch but not the other, the exact bug class already found and fixed twice in this binding
 * (DeviceNr 186 for MPL8_48_FH, and product code 662 for MRDA0610).
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
class DeviceTypeTest {

    @Test
    void everyConstantsOwnValueRoundTripsThroughCreateAndToString() {
        for (DeviceType type : DeviceType.values()) {
            if (type == DeviceType.Invalid) {
                continue;
            }
            int value = type.getValue();

            assertEquals(type, DeviceType.create(value),
                    "DeviceType.create(" + value + ") should resolve back to " + type.name());
            assertEquals(type.name(), type.toString(),
                    "toString() for " + type.name() + " (value " + value + ") doesn't match its own enum name");
        }
    }

    @Test
    void unknownValueResolvesToInvalid() {
        assertEquals(DeviceType.Invalid, DeviceType.create(-1));
    }

    @Test
    void aliasCode186ResolvesToMpl848Fh() {
        // Regression for the exact bug the class doc references: code 186 was missing from create()'s
        // switch even though MPL8_48_FH's own primary value round-tripped fine.
        assertEquals(DeviceType.MPL8_48_FH, DeviceType.create(186));
    }

    @Test
    void aliasCode662ResolvesToMrda0610432() {
        assertEquals(DeviceType.MRDA0610_432, DeviceType.create(662));
    }
}
