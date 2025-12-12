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
package org.openhab.binding.danfossally.internal;

import static org.openhab.binding.danfossally.internal.DanfossAllyBindingConstants.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 *
 * @author Stas Dovgodko <stas@dovgodko.dev> - Initial contribution
 */
@NonNullByDefault
public abstract class DanfossAllyDeviceHandler extends BaseThingHandler {

    @SuppressWarnings("unused")
    protected final Logger logger = LoggerFactory.getLogger(DanfossAllyDeviceHandler.class);

    private @Nullable ScheduledFuture<?> pollingJob;

    protected @Nullable DanfossAllyDeviceConfiguration config;

    public DanfossAllyDeviceHandler(Thing thing) {
        super(thing);
    }

    public String getDeviceId() {
        DanfossAllyDeviceConfiguration cfg = config;
        if (cfg == null) {
            return "";
        }

        return cfg.deviceId;
    }

    protected void pooling() {
        DanfossAllyBridgeHandler bridge = getBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge offline");
            return;
        }

        Random random = new Random();

        int interval = bridge.getPollingInterval();

        pollingJob = scheduler.scheduleWithFixedDelay(() -> {
            try {
                String id = getDeviceId();
                JSONArray status = bridge.getStatus(id);
                if (status == null) {
                    logger.warn("No status found for {} device", id);
                } else {
                    status(status);
                }
                updateStatus(ThingStatus.ONLINE);
            } catch (Exception e) {
                logger.warn("Error while polling Danfoss Ally device", e);
                updateStatus(ThingStatus.OFFLINE);
            }
        }, random.nextInt(interval), interval, TimeUnit.SECONDS);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);

        DanfossAllyDeviceConfiguration cfg = getConfigAs(DanfossAllyDeviceConfiguration.class);

        if (cfg.deviceId.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "deviceId not set");
            return;
        }

        this.config = cfg;

        pooling();

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
    }

    abstract protected HashMap<String, Object> command(ChannelUID channelUID, Command command);

    protected void populate(JSONObject deviceInfo) {
        // nothing todo
    }

    abstract protected void status(JSONArray deviceInfo);

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // poll();
            return;
        }

        DanfossAllyBridgeHandler bridge = getBridgeHandler();
        DanfossAllyDeviceConfiguration cfg = config;
        if (bridge == null || cfg == null) {
            return;
        }

        String deviceId = cfg.deviceId;
        if (deviceId.isEmpty()) {
            return;
        }

        // Аналог твоєї частини JS, де формується commands(...)
        JSONArray commands = new JSONArray();

        command(channelUID, command).forEach((key, value) -> {
            commands.put(new JSONObject().put("code", key).put("value", value));
        });

        if (commands.length() > 0) {
            boolean ok = bridge.sendCommands(deviceId, commands);
            if (ok) {
                /*
                 * switch (channelUID.getId()) {
                 * case CHANNEL_SETPOINT:
                 * updateState(channelUID, command);
                 * break;
                 * case CHANNEL_MODE:
                 * updateState(channelUID, command);
                 * break;
                 * }
                 */
            }
        }
    }

    protected void updateTemperatureChannel(String channelId, Object raw) {
        double v;

        if (raw instanceof Number n) {
            v = n.doubleValue();
        } else {
            try {
                v = Double.parseDouble(String.valueOf(raw));
            } catch (NumberFormatException e) {
                return;
            }
        }

        updateState(channelId, new QuantityType<>(v / 10.0, SIUnits.CELSIUS));
    }

    protected void updateTimestampChannel(String channelId, JSONObject deviceInfo, String fieldName) {
        if (!deviceInfo.has(fieldName)) {
            return;
        }

        Object raw = deviceInfo.get(fieldName);
        long ts;

        if (raw instanceof Number n) {
            ts = n.longValue();
        } else {
            try {
                ts = Long.parseLong(String.valueOf(raw));
            } catch (NumberFormatException e) {
                return;
            }
        }

        if (ts <= 0) {
            return;
        }

        // Хак: якщо значення > 1e12 – це, скоріш за все, мілісекунди, інакше секунди
        Instant instant = ts > 1_000_000_000_000L ? Instant.ofEpochMilli(ts) : Instant.ofEpochSecond(ts);

        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());

        updateState(channelId, new DateTimeType(zdt));
    }

    public void updateFromDevice(JSONObject deviceInfo) {
        // ---- online/sub/contact ----
        boolean online = deviceInfo.optBoolean("online", true);
        boolean sub = deviceInfo.optBoolean("sub", true);

        updateState(CHANNEL_ONLINE, online ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        updateState(CHANNEL_SUB, sub ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        updateStatus(online ? ThingStatus.ONLINE : ThingStatus.OFFLINE);

        updateTimestampChannel(CHANNEL_ACTIVE_TIME, deviceInfo, "active_time");
        updateTimestampChannel(CHANNEL_CREATE_TIME, deviceInfo, "create_time");
        updateTimestampChannel(CHANNEL_UPDATE_TIME, deviceInfo, "update_time");

        /*
         * JSONArray statusArray = deviceInfo.optJSONArray("status");
         * if (statusArray != null) {
         * status(statusArray);
         * }
         */

        populate(deviceInfo);
    }

    protected @Nullable DanfossAllyBridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        if (bridge.getHandler() instanceof DanfossAllyBridgeHandler handler) {
            return handler;
        }
        return null;
    }
}
