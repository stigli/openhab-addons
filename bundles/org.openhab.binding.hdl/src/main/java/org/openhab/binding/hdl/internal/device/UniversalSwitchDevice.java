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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;

/**
 * Implemented by {@link Device} subclasses that support HDL Buspro "Universal Switch" channels (a generic,
 * per-installation-configurable on/off flag; how many a given physical device exposes, and what each one is
 * assigned to, is decided in the HDL Setup Tool, not by this binding). Switch numbers are looked up
 * generically here instead of via fixed per-number fields/channels, since which numbers matter is a
 * per-Thing config choice (see the {@code UVSwitch} channel-type's {@code switchNumber} parameter) rather
 * than something this binding can enumerate in advance.
 *
 * @author stigla - Initial contribution
 */
@NonNullByDefault
public interface UniversalSwitchDevice {

    /**
     * @param switchNumber the 1-based universal switch number (HDL protocol range 1-255)
     * @return the switch's last known state, or {@code null} if never received
     */
    @Nullable
    OnOffType getUVSwitchState(int switchNumber);
}
