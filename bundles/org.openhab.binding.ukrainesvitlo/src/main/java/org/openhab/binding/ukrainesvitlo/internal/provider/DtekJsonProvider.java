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
package org.openhab.binding.ukrainesvitlo.internal.provider;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ukrainesvitlo.internal.config.RegionThingConfig;
import org.openhab.binding.ukrainesvitlo.internal.model.OutageData;
import org.openhab.binding.ukrainesvitlo.internal.model.OutageEvent;
import org.openhab.binding.ukrainesvitlo.internal.net.HttpHelper;
import org.openhab.binding.ukrainesvitlo.internal.util.EventUtil;
import org.openhab.binding.ukrainesvitlo.internal.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Port of ha-svitlo-yeah DTEK JSON provider (outage-data-ua / OE_OUTAGE_DATA JSON schema).
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class DtekJsonProvider implements OutageProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DtekJsonProvider.class);

    private final HttpHelper http;
    private final Gson gson = new Gson();

    public DtekJsonProvider(HttpHelper http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "dtek_json";
    }

    @Override
    public OutageData fetch(RegionThingConfig cfg, int timeoutMs) throws Exception {
        if (cfg.group == null || cfg.group.isBlank()) {
            throw new IllegalArgumentException("group is required for dtek_json (e.g. 3.1)");
        }
        String group = Objects.requireNonNull(cfg.group);
        List<String> urls = ProviderData.DTEK_PROVIDER_URLS.get(cfg.region);
        if (urls == null || urls.isEmpty()) {
            LOGGER.warn("DTEK available regions: {}", ProviderData.DTEK_PROVIDER_URLS.keySet());
            throw new IllegalArgumentException("Unknown region for dtek_json: " + cfg.region);
        }

        // Fetch first URL (HA tries multiple and checks freshness; simplified)
        String json = http.getText(urls.get(0), timeoutMs);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        JsonObject fact = root.has("fact") ? root.getAsJsonObject("fact") : root;
        @Nullable
        JsonObject preset = root.has("preset") ? root.getAsJsonObject("preset") : null;

        OutageData out = new OutageData();

        @Nullable
        ZonedDateTime updatedOn = parseTimestamp(fact.get("update"));
        out.scheduleUpdatedOn = updatedOn;

        out.plannedEvents = parsePlannedEvents(fact, group);
        out.plannedEvents = EventUtil.mergeAdjacent(out.plannedEvents);

        if (preset != null) {
            out.scheduledEvents = parseScheduledEvents(preset, group);
            out.scheduledEvents = EventUtil.mergeAdjacent(out.scheduledEvents);
        }

        computeDerived(out);

        return out;
    }

    private void computeDerived(OutageData out) {
        ZonedDateTime now = ZonedDateTime.now(TimeUtil.ZONE_UA);

        @Nullable
        OutageEvent current = EventUtil.getCurrent(out.plannedEvents, now);
        String electricity = "connected";
        if (current != null) {
            electricity = "planned_outage";
        }
        out.electricity = electricity;

        @Nullable
        OutageEvent nextPlanned = EventUtil.getNextOfType(out.plannedEvents, now, "Definite");
        if (nextPlanned != null) {
            out.nextPlannedOutage = nextPlanned.start;
        }

        // nextConnectivity
        if (current != null) {
            out.nextConnectivity = current.end;
        } else if (nextPlanned != null) {
            out.nextConnectivity = nextPlanned.end;
        }

        // nextScheduledOutage = earliest of scheduled or planned
        @Nullable
        OutageEvent nextSched = EventUtil.getNextOfType(out.scheduledEvents, now, "Scheduled");
        @Nullable
        ZonedDateTime cand = null;
        if (nextSched != null)
            cand = nextSched.start;
        if (out.nextPlannedOutage != null) {
            if (cand == null || out.nextPlannedOutage.isBefore(cand))
                cand = out.nextPlannedOutage;
        }
        out.nextScheduledOutage = cand;
    }

    private List<OutageEvent> parsePlannedEvents(@Nullable JsonObject fact, String group) {
        List<OutageEvent> events = new ArrayList<>();
        if (fact == null || !fact.has("data"))
            return events;

        String groupKey = "GPV" + group;
        JsonObject data = fact.getAsJsonObject("data");
        for (Map.Entry<String, JsonElement> dayEntry : data.entrySet()) {
            String tsStr = dayEntry.getKey();
            JsonObject dayObj = dayEntry.getValue().getAsJsonObject();
            if (!dayObj.has(groupKey))
                continue;

            ZonedDateTime day = Instant.ofEpochSecond(Long.parseLong(tsStr)).atZone(ZoneId.of("UTC"))
                    .withZoneSameInstant(TimeUtil.ZONE_UA).toLocalDate().atStartOfDay(TimeUtil.ZONE_UA);

            JsonObject hours = dayObj.getAsJsonObject(groupKey);
            // hours may be keyed 1..24 or 0..23
            int maxHour = hours.has("24") ? 24 : 23;
            boolean startsAtOne = hours.has("1") && !hours.has("0");

            @Nullable
            ZonedDateTime outageStart = null;
            for (int i = 0; i <= maxHour; i++) {
                String key = startsAtOne ? Integer.toString(i == 0 ? 1 : i + 1) : Integer.toString(i);
                if (!hours.has(key))
                    continue;
                String status = hours.get(key).getAsString();

                int hour = startsAtOne ? (Integer.parseInt(key) - 1) : Integer.parseInt(key);

                boolean powerOn = "yes".equalsIgnoreCase(status);
                if (powerOn) {
                    if (outageStart != null) {
                        ZonedDateTime end = day.plusHours(hour);
                        events.add(new OutageEvent(outageStart, end, "Definite", "Planned outage"));
                        outageStart = null;
                    }
                    continue;
                }

                // outage statuses: no/first/mfirst/second/msecond
                if (outageStart == null) {
                    int minute = ("second".equalsIgnoreCase(status) || "msecond".equalsIgnoreCase(status)) ? 30 : 0;
                    outageStart = day.plusHours(hour).plusMinutes(minute);
                }

                // Handle 30-minute end for "first/mfirst"
                if ("first".equalsIgnoreCase(status) || "mfirst".equalsIgnoreCase(status)) {
                    ZonedDateTime end = day.plusHours(hour).plusMinutes(30);
                    events.add(new OutageEvent(outageStart, end, "Definite", "Planned outage"));
                    outageStart = null;
                }
            }

            if (outageStart != null) {
                events.add(new OutageEvent(outageStart, day.plusDays(1), "Definite", "Planned outage"));
            }
        }
        events.sort((a, b) -> a.start.compareTo(b.start));
        return events;
    }

    private List<OutageEvent> parseScheduledEvents(@Nullable JsonObject preset, String group) {
        List<OutageEvent> events = new ArrayList<>();
        if (preset == null || !preset.has("data"))
            return events;

        String groupKey = "GPV" + group;
        JsonObject data = preset.getAsJsonObject("data");
        for (Map.Entry<String, JsonElement> dayEntry : data.entrySet()) {
            String tsStr = dayEntry.getKey();
            JsonObject dayObj = dayEntry.getValue().getAsJsonObject();
            if (!dayObj.has(groupKey))
                continue;

            ZonedDateTime day = Instant.ofEpochSecond(Long.parseLong(tsStr)).atZone(ZoneId.of("UTC"))
                    .withZoneSameInstant(TimeUtil.ZONE_UA).toLocalDate().atStartOfDay(TimeUtil.ZONE_UA);

            JsonObject hours = dayObj.getAsJsonObject(groupKey);
            // treat any non-yes as outage for scheduled
            int maxHour = hours.has("24") ? 24 : 23;
            boolean startsAtOne = hours.has("1") && !hours.has("0");
            @Nullable
            ZonedDateTime outageStart = null;

            for (int i = 0; i <= maxHour; i++) {
                String key = startsAtOne ? Integer.toString(i == 0 ? 1 : i + 1) : Integer.toString(i);
                if (!hours.has(key))
                    continue;
                String status = hours.get(key).getAsString();
                int hour = startsAtOne ? (Integer.parseInt(key) - 1) : Integer.parseInt(key);

                boolean powerOn = "yes".equalsIgnoreCase(status);
                if (powerOn) {
                    if (outageStart != null) {
                        ZonedDateTime end = day.plusHours(hour);
                        events.add(new OutageEvent(outageStart, end, "Scheduled", "Scheduled outage"));
                        outageStart = null;
                    }
                    continue;
                }

                if (outageStart == null) {
                    outageStart = day.plusHours(hour);
                }
            }

            if (outageStart != null) {
                events.add(new OutageEvent(outageStart, day.plusDays(1), "Scheduled", "Scheduled outage"));
            }
        }
        events.sort((a, b) -> a.start.compareTo(b.start));
        return events;
    }

    private @Nullable ZonedDateTime parseTimestamp(@Nullable JsonElement el) {
        if (el == null || el.isJsonNull())
            return null;
        String s = el.getAsString();
        if (s == null || s.isBlank())
            return null;

        // ha-svitlo-yeah uses parse_timestamp helper which supports multiple formats.
        // Best-effort:
        try {
            return ZonedDateTime.parse(s).withZoneSameInstant(TimeUtil.ZONE_UA);
        } catch (DateTimeParseException ignored) {
        }
        try {
            long epoch = Long.parseLong(s);
            return Instant.ofEpochSecond(epoch).atZone(TimeUtil.ZONE_UA);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
