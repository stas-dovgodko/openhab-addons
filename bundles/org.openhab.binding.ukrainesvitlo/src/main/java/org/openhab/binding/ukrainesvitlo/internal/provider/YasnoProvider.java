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

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Port of ha-svitlo-yeah Yasno API logic.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class YasnoProvider implements OutageProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(YasnoProvider.class);

    private static final String REGIONS_ENDPOINT = "https://app.yasno.ua/api/blackout-service/public/shutdowns/addresses/v2/regions";
    private static final String PLANNED_ENDPOINT = "https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/{region_id}/dsos/{dso_id}/planned-outages";

    private final HttpHelper http;
    private final Gson gson = new Gson();

    public YasnoProvider(HttpHelper http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "yasno";
    }

    @Override
    public OutageData fetch(RegionThingConfig cfg, int timeoutMs) throws Exception {
        if (cfg.group == null || cfg.group.isBlank()) {
            throw new IllegalArgumentException("group is required for yasno (e.g. 3.1)");
        }
        if (cfg.region == null || cfg.region.isBlank()) {
            throw new IllegalArgumentException("region is required for yasno");
        }

        String region = Objects.requireNonNull(cfg.region);
        String group = Objects.requireNonNull(cfg.group);

        // Resolve region_id and dso_id
        RegionDso rd = resolveRegionAndDso(region, timeoutMs);
        String url = PLANNED_ENDPOINT.replace("{region_id}", Integer.toString(rd.regionId)).replace("{dso_id}",
                Integer.toString(rd.dsoId));

        String json = http.getText(url, timeoutMs);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        // payload keyed by group, e.g. "3.1"
        @Nullable
        JsonObject groupObj = root.getAsJsonObject(group);
        if (groupObj == null) {
            throw new IllegalStateException("Group not found in response: " + group);
        }

        OutageData out = new OutageData();
        out.scheduleUpdatedOn = TimeUtil.parseIsoToUa(getString(groupObj, "updatedOn"));

        // Parse today/tomorrow day blocks into planned events
        out.plannedEvents = new ArrayList<>();
        parseDayBlock(out.plannedEvents, groupObj.getAsJsonObject("today"));
        parseDayBlock(out.plannedEvents, groupObj.getAsJsonObject("tomorrow"));

        out.plannedEvents = EventUtil.mergeAdjacent(out.plannedEvents);

        computeDerived(out);

        return out;
    }

    private void computeDerived(OutageData out) {
        ZonedDateTime now = ZonedDateTime.now(TimeUtil.ZONE_UA);

        // current planned event?
        @Nullable
        OutageEvent current = EventUtil.getCurrent(out.plannedEvents, now);
        String electricity = "connected";
        if (current != null) {
            if ("Emergency".equalsIgnoreCase(current.type)) {
                electricity = "emergency";
            } else {
                electricity = "planned_outage";
            }
        }
        out.electricity = electricity;

        @Nullable
        OutageEvent nextPlanned = EventUtil.getNextOfType(out.plannedEvents, now, "Definite");
        if (nextPlanned != null) {
            out.nextPlannedOutage = nextPlanned.start;
        }

        // nextConnectivity: if currently in planned outage => end of current, else end of next outage
        if (current != null && "Definite".equalsIgnoreCase(current.type)) {
            out.nextConnectivity = current.end;
        } else if (nextPlanned != null) {
            out.nextConnectivity = nextPlanned.end;
        }

        // nextScheduledOutage isn't available from Yasno planned endpoint; set to next planned
        out.nextScheduledOutage = out.nextPlannedOutage;
    }

    private void parseDayBlock(List<OutageEvent> events, @Nullable JsonObject dayBlock) {
        if (dayBlock == null)
            return;

        @Nullable
        String status = getString(dayBlock, "status");
        @Nullable
        String dateStr = getString(dayBlock, "date");
        @Nullable
        ZonedDateTime day = TimeUtil.parseIsoToUa(dateStr);
        if (day == null)
            return;

        if ("EmergencyShutdowns".equalsIgnoreCase(status)) {
            // whole day emergency (best-effort): create event 00:00-24:00
            ZonedDateTime start = day.truncatedTo(ChronoUnit.DAYS);
            ZonedDateTime end = start.plusDays(1);
            events.add(new OutageEvent(start, end, "Emergency", "Emergency outage"));
            return;
        }

        if (!"ScheduleApplies".equalsIgnoreCase(status)) {
            return;
        }

        JsonArray slots = dayBlock.getAsJsonArray("slots");
        if (slots == null)
            return;

        for (JsonElement el : slots) {
            if (!el.isJsonObject())
                continue;
            JsonObject slot = el.getAsJsonObject();
            String type = getString(slot, "type");
            if (!"Definite".equalsIgnoreCase(type)) {
                continue;
            }
            int startMin = slot.get("start").getAsInt();
            int endMin = slot.get("end").getAsInt();

            ZonedDateTime start = day.truncatedTo(ChronoUnit.DAYS).plusMinutes(startMin);
            ZonedDateTime end = day.truncatedTo(ChronoUnit.DAYS).plusMinutes(endMin);
            events.add(new OutageEvent(start, end, "Definite", "Planned outage"));
        }
    }

    private static @Nullable String getString(@Nullable JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
            return null;
        return obj.get(key).getAsString();
    }

    private RegionDso resolveRegionAndDso(String regionKey, int timeoutMs) throws Exception {
        String json = http.getText(REGIONS_ENDPOINT, timeoutMs);
        JsonArray arr = gson.fromJson(json, JsonArray.class);

        String desired = normalizeRegionKey(regionKey);
        JsonObject bestRegion = null;
        List<String> available = new ArrayList<>();

        for (JsonElement el : arr) {
            if (!el.isJsonObject())
                continue;
            JsonObject reg = el.getAsJsonObject();
            String value = getString(reg, "value"); // Ukrainian name
            if (value != null && !value.isBlank()) {
                available.add(value);
            }
            String norm = normalizeRegionKey(value);
            if (norm.equals(desired) || norm.contains(desired) || desired.contains(norm)) {
                bestRegion = reg;
                break;
            }
        }

        // fallback: use first
        if (bestRegion == null && arr.size() > 0) {
            if (!available.isEmpty()) {
                LOGGER.warn("Yasno available regions: {}", available);
            }
            bestRegion = arr.get(0).getAsJsonObject();
            LOGGER.warn("Yasno region '{}' not matched; using first region '{}'", regionKey,
                    getString(bestRegion, "value"));
        }

        if (bestRegion == null) {
            throw new IllegalStateException("No Yasno regions returned");
        }

        int regionId = bestRegion.get("id").getAsInt();
        JsonArray dsos = bestRegion.getAsJsonArray("dsos");
        if (dsos == null || dsos.size() == 0) {
            throw new IllegalStateException("No dsos in Yasno region response");
        }

        JsonObject dso = dsos.get(0).getAsJsonObject();
        int dsoId = dso.get("id").getAsInt();

        return new RegionDso(regionId, dsoId);
    }

    private static String normalizeRegionKey(@Nullable String s) {
        if (s == null)
            return "";
        return s.toLowerCase().replace("область", "").replace("обл.", "").replace("місто", "").replace("м.", "")
                .replace(" ", "").replace("-", "").replace("_", "");
    }

    private static class RegionDso {
        final int regionId;
        final int dsoId;

        RegionDso(int regionId, int dsoId) {
            this.regionId = regionId;
            this.dsoId = dsoId;
        }
    }
}
