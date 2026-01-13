/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.ukrainesvitlo.internal.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Date/time helpers for UA timezone.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class TimeUtil {
    public static final ZoneId ZONE_UA = ZoneId.of("Europe/Kyiv");

    private TimeUtil() {
    }

    public static @Nullable ZonedDateTime parseIsoToUa(@Nullable String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(iso).withZoneSameInstant(ZONE_UA);
        } catch (DateTimeParseException e) {
            // some API returns without zone; ignore
            return null;
        }
    }
}
