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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeUID;
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

    public void updateChannels(JSONObject deviceInfo) {
        logger.debug("Starting channel update");

        JSONArray statusArray = deviceInfo.optJSONArray("status");
        if (statusArray == null || statusArray.length() == 0) {
            logger.warn("No status array found in device info");
            return;
        }

        List<Channel> channels = new ArrayList<>(this.getThing().getChannels().stream()
                // .filter(ch -> ch.getChannelTypeUID() == null || !ch.getUID().getId().startsWith("status"))
                .collect(Collectors.toList()));

        ChannelGroupUID groupUID = new ChannelGroupUID(this.getThing().getUID(), "status");
        boolean changed = false;
        for (int i = 0; i < statusArray.length(); i++) {
            try {
                JSONObject s = statusArray.getJSONObject(i);
                String code = s.getString("code");
                Object value = s.get("value");

                String channelId = code.replaceAll("[^A-Za-z0-9_]", "_");
                ChannelUID channelUID = new ChannelUID(groupUID, channelId);
                ChannelTypeUID ct;

                boolean channelExists = channels.stream().anyMatch(ch -> ch.getUID().equals(channelUID));

                if (!channelExists) {
                    // Визначаємо тип Item на основі типу значення
                    String itemType;
                    if (value instanceof Number) {
                        itemType = "Number";
                        ct = new ChannelTypeUID(BINDING_ID, "number");
                    } else if (value instanceof Boolean) {
                        itemType = "Switch";
                        ct = new ChannelTypeUID(BINDING_ID, "switch");
                    } else {
                        itemType = "String";
                        ct = new ChannelTypeUID(BINDING_ID, "string");
                    }

                    changed = channels
                            .add(ChannelBuilder.create(channelUID, itemType).withType(ct).withLabel("Status " + code)
                                    .withKind(ChannelKind.STATE).withDescription("API status for " + code).build());
                    logger.info("Added dynamic channel: {} (type: {})", channelId, itemType);

                }
            } catch (Exception e) {
                logger.error("Error processing status item {}: {}", i, e.getMessage(), e);
            }
        }

        if (changed) {
            updateThing(editThing().withChannels(channels).build());
        }
    }

    public void refresh() {
        try {
            String id = getDeviceId();
            JSONObject status = getBridgeHandler().getDevice(id);
            ChannelGroupUID groupUID = new ChannelGroupUID(this.getThing().getUID(), "status");
            if (status == null) {
                logger.warn("No status found for {} device", id);
            } else {
                logger.info("Refresh for {} device", id);
                JSONArray statusArray = status.optJSONArray("status");

                for (int i = 0; i < statusArray.length(); i++) {
                    JSONObject s = statusArray.getJSONObject(i);
                    String code = s.getString("code");
                    Object value = s.get("value");

                    String channelId = code.replaceAll("[^A-Za-z0-9_]", "_");
                    ChannelUID channelUID = new ChannelUID(groupUID, channelId);

                    if (value instanceof Boolean b) {
                        updateState(channelUID, b.booleanValue() ? OnOffType.ON : OnOffType.OFF);
                    } else if (value instanceof Number n) {
                        updateState(channelUID, new DecimalType(n));
                    } else {
                        updateState(channelUID, new StringType(value.toString()));
                    }
                }

                status(statusArray);
            }
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            logger.warn("Error while polling Danfoss Ally device", e);
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    protected void pooling(int interval) {
        Random random = new Random();

        pollingJob = scheduler.scheduleWithFixedDelay(() -> {
            refresh();
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

        DanfossAllyBridgeHandler bridge = getBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge offline");
            return;
        }

        pooling(bridge.getPollingInterval());

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

    abstract protected void status(JSONArray deviceInfo);

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refresh();
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

        JSONArray commands = new JSONArray();

        logger.debug("Command in {} channel( {} group)", channelUID.getAsString(), channelUID.getGroupId());

        if (channelUID.getGroupId().equals("status")) {
            Object value;
            if (command instanceof OnOffType of) {
                value = of.equals(OnOffType.ON);
            } else if (command instanceof DecimalType dt) {
                value = dt.doubleValue();
            } else {
                value = command.toFullString();
            }
            commands.put(new JSONObject().put("code", channelUID.getId().substring(7)).put("value", value));
        }

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

        Instant instant = ts > 1_000_000_000_000L ? Instant.ofEpochMilli(ts) : Instant.ofEpochSecond(ts);

        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());

        updateState(channelId, new DateTimeType(zdt));
    }

    public void updateFromDevice(JSONObject deviceInfo) {

        updateChannels(deviceInfo);

        // ---- online/sub/contact ----
        boolean online = deviceInfo.optBoolean("online", true);
        boolean sub = deviceInfo.optBoolean("sub", true);

        updateState(CHANNEL_ONLINE, online ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        updateState(CHANNEL_SUB, sub ? OpenClosedType.OPEN : OpenClosedType.CLOSED);

        updateTimestampChannel(CHANNEL_ACTIVE_TIME, deviceInfo, "active_time");
        updateTimestampChannel(CHANNEL_CREATE_TIME, deviceInfo, "create_time");
        updateTimestampChannel(CHANNEL_UPDATE_TIME, deviceInfo, "update_time");

        updateStatus(online ? ThingStatus.ONLINE : ThingStatus.OFFLINE);
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
