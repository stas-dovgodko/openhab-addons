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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ukrainesvitlo.internal.model.OutageEvent;

/**
 * Utility helpers for outage event lists.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class EventUtil {

    private EventUtil() {
    }

    public static List<OutageEvent> mergeAdjacent(@Nullable List<OutageEvent> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<OutageEvent> events = new ArrayList<>(input);
        events.sort(Comparator.comparing(e -> e.start));

        List<OutageEvent> out = new ArrayList<>();
        OutageEvent cur = events.get(0);
        for (int i = 1; i < events.size(); i++) {
            OutageEvent nxt = events.get(i);
            if (cur.type != null && cur.type.equals(nxt.type) && !cur.end.isBefore(nxt.start)
                    && !cur.end.isAfter(nxt.start.plusSeconds(0))) {
                // exact adjacency
                cur.end = nxt.end;
                continue;
            }
            if (cur.type != null && cur.type.equals(nxt.type) && cur.end.equals(nxt.start)) {
                cur.end = nxt.end;
                continue;
            }
            out.add(cur);
            cur = nxt;
        }
        out.add(cur);
        return out;
    }

    public static @Nullable OutageEvent getCurrent(@Nullable List<OutageEvent> events, @Nullable ZonedDateTime now) {
        if (events == null || now == null)
            return null;
        for (OutageEvent e : events) {
            if ((e.start.isEqual(now) || e.start.isBefore(now)) && e.end.isAfter(now)) {
                return e;
            }
        }
        return null;
    }

    public static @Nullable ZonedDateTime getFirstFutureStart(@Nullable List<OutageEvent> events,
            @Nullable ZonedDateTime now) {
        if (events == null || now == null)
            return null;
        ZonedDateTime best = null;
        for (OutageEvent e : events) {
            if (e.start.isAfter(now)) {
                if (best == null || e.start.isBefore(best))
                    best = e.start;
            }
        }
        return best;
    }

    public static @Nullable OutageEvent getNextOfType(@Nullable List<OutageEvent> events, @Nullable ZonedDateTime now,
            @Nullable String type) {
        if (events == null || now == null || type == null)
            return null;
        OutageEvent best = null;
        for (OutageEvent e : events) {
            if (!type.equals(e.type))
                continue;
            if (e.start.isAfter(now)) {
                if (best == null || e.start.isBefore(best.start))
                    best = e;
            }
        }
        return best;
    }
}
