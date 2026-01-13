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
package org.openhab.binding.ukrainesvitlo.internal.handler;

import static org.openhab.binding.ukrainesvitlo.internal.UkrainesvitloBindingConstants.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ukrainesvitlo.internal.config.RegionThingConfig;
import org.openhab.binding.ukrainesvitlo.internal.model.OutageData;
import org.openhab.binding.ukrainesvitlo.internal.provider.OutageProvider;
import org.openhab.binding.ukrainesvitlo.internal.provider.OutageProviderFactory;
import org.openhab.binding.ukrainesvitlo.internal.util.TimeUtil;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Thing handler for regional outage schedules.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class UkrainesvitloRegionThingHandler extends BaseThingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UkrainesvitloRegionThingHandler.class);

    private final OutageProviderFactory providerFactory;
    private final Gson gson = new Gson();

    private @Nullable ScheduledFuture<?> refreshJob;

    private @Nullable String lastScheduleHash;
    private @Nullable ZonedDateTime lastDataChangedOn;

    public UkrainesvitloRegionThingHandler(Thing thing, OutageProviderFactory providerFactory) {
        super(thing);
        this.providerFactory = providerFactory;
    }

    @Override
    public void initialize() {
        RegionThingConfig cfg = getConfigAs(RegionThingConfig.class);

        if (cfg.region == null || cfg.region.isBlank() || cfg.provider == null || cfg.provider.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "region/provider are required");
            return;
        }

        int refreshSeconds = resolveRefreshSeconds(cfg);

        refreshJob = scheduler.scheduleWithFixedDelay(this::safeRefresh, 0, refreshSeconds, TimeUnit.SECONDS);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
            refreshJob = null;
        }
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No writable channels; ignore commands.
    }

    private int resolveRefreshSeconds(RegionThingConfig cfg) {
        Integer own = cfg.refreshSeconds;

        // If bridge has defaults, use them as fallback
        int bridgeDefault = 300;

        var bridgeHandler = getBridge() != null ? getBridge().getHandler() : null;
        if (bridgeHandler instanceof UkrainesvitloServiceBridgeHandler b) {
            var bc = b.getBridgeConfig();
            if (bc.refreshSeconds != null)
                bridgeDefault = bc.refreshSeconds;
        }

        return (own != null) ? own : bridgeDefault;
    }

    private int resolveTimeoutMs() {
        int bridgeTimeout = 8000;
        var bridgeHandler = getBridge() != null ? getBridge().getHandler() : null;
        if (bridgeHandler instanceof UkrainesvitloServiceBridgeHandler b) {
            var bc = b.getBridgeConfig();
            if (bc.httpTimeoutMs != null)
                bridgeTimeout = bc.httpTimeoutMs;
        }
        return bridgeTimeout;
    }

    private void safeRefresh() {
        try {
            RegionThingConfig cfg = getConfigAs(RegionThingConfig.class);
            int timeoutMs = resolveTimeoutMs();

            String providerId = cfg.provider;
            if (providerId == null || providerId.isBlank()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "provider is required");
                return;
            }
            OutageProvider provider = providerFactory.get(providerId);
            OutageData data = provider.fetch(cfg, timeoutMs);

            // schedule change detection hash
            @Nullable
            String hash = hashSchedule(data);
            if (hash != null && (lastScheduleHash == null || !hash.equals(lastScheduleHash))) {
                lastScheduleHash = hash;
                lastDataChangedOn = ZonedDateTime.now(TimeUtil.ZONE_UA);
                triggerChannel(CH_DATA_CHANGED, "changed");
            }

            updateState(CH_ELECTRICITY, new StringType(nullToEmpty(data.electricity)));

            updateDateTime(CH_SCHEDULE_UPDATED_ON, data.scheduleUpdatedOn);
            updateDateTime(CH_SCHEDULE_DATA_CHANGED_ON, lastDataChangedOn);

            updateDateTime(CH_NEXT_PLANNED_OUTAGE, data.nextPlannedOutage);
            updateDateTime(CH_NEXT_SCHEDULED_OUTAGE, data.nextScheduledOutage);
            updateDateTime(CH_NEXT_CONNECTIVITY, data.nextConnectivity);

            updateState(CH_PLANNED_JSON, new StringType(gson.toJson(data.plannedEvents)));
            updateState(CH_SCHEDULED_JSON, new StringType(gson.toJson(data.scheduledEvents)));

            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            LOGGER.debug("Refresh failed", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private void updateDateTime(String channelId, @Nullable ZonedDateTime dt) {
        if (dt == null) {
            updateState(channelId, UnDefType.UNDEF);
            return;
        }
        updateState(channelId, new DateTimeType(dt));
    }

    private static String nullToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    private @Nullable String hashSchedule(OutageData data) {
        try {
            String payload = gson.toJson(Map.of("planned", data.plannedEvents, "scheduled", data.scheduledEvents));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
