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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Port of ha-svitlo-yeah E-Svitlo client.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class ESvitloProvider implements OutageProvider {

    private final HttpHelper http;
    private final Gson gson = new Gson();

    public ESvitloProvider(HttpHelper http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "e-svitlo";
    }

    @Override
    public OutageData fetch(RegionThingConfig cfg, int timeoutMs) throws Exception {
        if (cfg.username == null || cfg.username.isBlank() || cfg.password == null || cfg.password.isBlank()) {
            throw new IllegalArgumentException("username/password are required for e-svitlo");
        }

        String username = Objects.requireNonNull(cfg.username);
        String password = Objects.requireNonNull(cfg.password);
        Session session = login(username, password, timeoutMs);

        @Nullable
        String accountId = cfg.accountId;
        if (accountId == null || accountId.isBlank()) {
            accountId = fetchFirstAccountId(session, timeoutMs);
        }
        if (accountId == null) {
            throw new IllegalStateException("No accountId available for e-svitlo");
        }

        @Nullable
        String group = fetchGroup(session, accountId, timeoutMs);
        if (group == null || group.isBlank()) {
            group = "auto";
        }

        // request disconnections
        @Nullable
        JsonObject resp = postJson(session, "api_main/get_user_disconnections_image_api.json",
                Map.of("a", accountId, "cherga", group, "mobile_v", "True"), timeoutMs);

        OutageData out = new OutageData();
        if (resp == null) {
            return out;
        }

        // parse last_update
        @Nullable
        String lastUpdate = null;
        @Nullable
        JsonObject data = resp.has("data") ? resp.getAsJsonObject("data") : null;
        if (data != null) {
            if (data.has("dict_tom") && data.getAsJsonObject("dict_tom").has("last_update")) {
                lastUpdate = data.getAsJsonObject("dict_tom").get("last_update").getAsString();
            } else if (data.has("last_update")) {
                lastUpdate = data.get("last_update").getAsString();
            }
        }
        out.scheduleUpdatedOn = parseUaLastUpdate(lastUpdate);

        out.plannedEvents = parseDisconnections(resp);
        out.plannedEvents = EventUtil.mergeAdjacent(out.plannedEvents);

        computeDerived(out);

        return out;
    }

    private void computeDerived(OutageData out) {
        ZonedDateTime now = ZonedDateTime.now(TimeUtil.ZONE_UA);
        @Nullable
        OutageEvent current = EventUtil.getCurrent(out.plannedEvents, now);

        out.electricity = (current != null) ? "planned_outage" : "connected";

        @Nullable
        OutageEvent nextPlanned = EventUtil.getNextOfType(out.plannedEvents, now, "Definite");
        if (nextPlanned != null)
            out.nextPlannedOutage = nextPlanned.start;

        if (current != null)
            out.nextConnectivity = current.end;
        else if (nextPlanned != null)
            out.nextConnectivity = nextPlanned.end;

        out.nextScheduledOutage = out.nextPlannedOutage;
    }

    private @Nullable ZonedDateTime parseUaLastUpdate(@Nullable String s) {
        if (s == null)
            return null;
        // format: "Оновлено: 13.12.2025 10:59"
        String cleaned = s.replace("Оновлено:", "").trim();
        try {
            return ZonedDateTime.of(
                    LocalDate.parse(cleaned.substring(0, 10), DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    LocalTime.parse(cleaned.substring(11), DateTimeFormatter.ofPattern("HH:mm")), TimeUtil.ZONE_UA);
        } catch (Exception ignored) {
        }
        return ZonedDateTime.now(TimeUtil.ZONE_UA);
    }

    private List<OutageEvent> parseDisconnections(@Nullable JsonObject resp) {
        List<OutageEvent> events = new ArrayList<>();
        if (resp == null || !resp.has("data"))
            return events;
        JsonObject data = resp.getAsJsonObject("data");

        // Today periods
        if (data.has("lst_time_disc") && data.get("lst_time_disc").isJsonArray()) {
            String dateStr = data.has("date_today") ? data.get("date_today").getAsString() : null;
            events.addAll(parseDayPeriods(data.getAsJsonArray("lst_time_disc"), dateStr));
        }

        // Tomorrow dict_tom.lst_time_disc
        if (data.has("dict_tom")) {
            JsonObject tom = data.getAsJsonObject("dict_tom");
            if (tom.has("lst_time_disc") && tom.get("lst_time_disc").isJsonArray()) {
                String dateStr = tom.has("date_tom") ? tom.get("date_tom").getAsString()
                        : (data.has("date_tom") ? data.get("date_tom").getAsString() : null);
                events.addAll(parseDayPeriods(tom.getAsJsonArray("lst_time_disc"), dateStr));
            }
        }

        events.sort((a, b) -> a.start.compareTo(b.start));
        return events;
    }

    private List<OutageEvent> parseDayPeriods(@Nullable JsonArray periods, @Nullable String dateStr) {
        List<OutageEvent> out = new ArrayList<>();
        if (periods == null || dateStr == null)
            return out;

        LocalDate base;
        try {
            base = LocalDate.parse(dateStr);
        } catch (Exception e) {
            // sometimes it's dd.MM.yyyy
            try {
                base = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } catch (Exception ignored) {
                return out;
            }
        }

        for (JsonElement el : periods) {
            if (!el.isJsonObject())
                continue;
            JsonObject p = el.getAsJsonObject();
            String startStr = p.has("start_time") ? p.get("start_time").getAsString() : null;
            String endStr = p.has("end_time") ? p.get("end_time").getAsString() : null;
            if (startStr == null || endStr == null)
                continue;

            LocalTime st;
            LocalTime en;
            try {
                st = LocalTime.parse(startStr);
                en = LocalTime.parse(endStr);
            } catch (Exception e) {
                continue;
            }

            ZonedDateTime start = ZonedDateTime.of(base, st, TimeUtil.ZONE_UA);
            ZonedDateTime end = ZonedDateTime.of(base, en, TimeUtil.ZONE_UA);
            if (en.isBefore(st)) {
                end = end.plusDays(1);
            }
            out.add(new OutageEvent(start, end, "Definite", "Planned outage"));
        }

        return out;
    }

    // --- Session & HTTP ---

    private Session login(String username, String password, int timeoutMs) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("login", username);
        form.put("password", password);

        @Nullable
        JsonObject resp = postJson(null, "api_main/login_api.json", form, timeoutMs);
        if (resp == null || !resp.has("data")) {
            throw new IllegalStateException("E-Svitlo login failed");
        }
        JsonObject data = resp.getAsJsonObject("data");
        String token = data.has("token") ? data.get("token").getAsString() : null;
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("E-Svitlo login token missing");
        }
        return new Session(token);
    }

    private @Nullable String fetchFirstAccountId(Session session, int timeoutMs) throws Exception {
        @Nullable
        JsonObject resp = postJson(session, "api_main_reg/short_list_ls_api.json", Map.of(), timeoutMs);
        if (resp == null || !resp.has("data"))
            return null;
        JsonObject data = resp.getAsJsonObject("data");
        JsonArray lst = data.has("lst_ls") ? data.getAsJsonArray("lst_ls") : null;
        if (lst == null || lst.size() == 0)
            return null;

        JsonObject first = lst.get(0).getAsJsonObject();
        return first.has("a") ? first.get("a").getAsString() : null;
    }

    private @Nullable String fetchGroup(Session session, String accountId, int timeoutMs) throws Exception {
        @Nullable
        JsonObject resp = postJson(session, "/api_main_reg/all_details_ls_api.json", Map.of("a", accountId), timeoutMs);
        if (resp == null || !resp.has("data"))
            return null;
        JsonObject data = resp.getAsJsonObject("data");
        if (!data.has("lst_cherga") || !data.get("lst_cherga").isJsonArray())
            return null;
        JsonArray arr = data.getAsJsonArray("lst_cherga");
        if (arr.size() == 0)
            return null;
        return arr.get(0).getAsString();
    }

    private @Nullable JsonObject postJson(@Nullable Session session, String endpoint, Map<String, String> form,
            int timeoutMs) throws Exception {
        String ep = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint;
        String url = ProviderData.E_SVITLO_BASE + ep;

        // In HA implementation, token is sent as cookie/headers implicitly (session).
        // Here we attach as header if present.
        var reqForm = new HashMap<>(form);

        String body;
        if (session != null && session.token != null) {
            // API accepts token param in some endpoints; use header + param for best compatibility
            reqForm.putIfAbsent("token", session.token);
        }

        body = http.postForm(url, reqForm, timeoutMs);
        return gson.fromJson(body, JsonObject.class);
    }

    private static class Session {
        final String token;

        Session(String token) {
            this.token = token;
        }
    }
}
