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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
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
public class DanfossAllyDeviceHandler extends BaseThingHandler {

    @SuppressWarnings("unused")
    private final Logger logger = LoggerFactory.getLogger(DanfossAllyDeviceHandler.class);

    private @Nullable DanfossAllyDeviceConfiguration config;

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

    @Override
    public void initialize() {
        config = getConfigAs(DanfossAllyDeviceConfiguration.class);

        DanfossAllyBridgeHandler bridge = getBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge offline");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);
    }

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

        switch (channelUID.getId()) {
            case CHANNEL_SETPOINT:
                if (command instanceof QuantityType<?> q) {
                    double value = q.toBigDecimal().doubleValue();
                    JSONObject cmd = new JSONObject().put("code", "temp_set").put("value", Math.round(value));
                    commands.put(cmd);
                }
                break;
            case CHANNEL_MODE:
                if (command instanceof StringType s) {
                    JSONObject cmd = new JSONObject().put("code", "mode").put("value", s.toString());
                    commands.put(cmd);
                }
                break;
            default:
                break;
        }

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

    private void updateTemperatureChannel(String channelId, Object raw) {
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

    private void updateTimestampChannel(String channelId, JSONObject deviceInfo, String fieldName) {
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

        // ---- DateTime поля ----
        updateTimestampChannel(CHANNEL_ACTIVE_TIME, deviceInfo, "active_time");
        updateTimestampChannel(CHANNEL_CREATE_TIME, deviceInfo, "create_time");
        updateTimestampChannel(CHANNEL_UPDATE_TIME, deviceInfo, "update_time");

        // ---- status[] -> map code -> value ----
        JSONArray statusArray = deviceInfo.optJSONArray("status");
        if (statusArray == null) {
            return;
        }

        Double tempSet = null;
        Double tempCurrent = null;
        String mode = null;
        boolean active = false;

        for (int i = 0; i < statusArray.length(); i++) {
            JSONObject s = statusArray.getJSONObject(i);
            String code = s.getString("code");
            Object value = s.get("value");

            switch (code) {
                case "temp_set":
                    if (value instanceof Number n) {
                        tempSet = n.doubleValue();
                    }
                    break;
                case "temp_current":
                    if (value instanceof Number n) {
                        tempCurrent = n.doubleValue();
                    }
                    break;
                case "mode":
                    mode = String.valueOf(value);
                    break;
                case "switch_state":
                    active = s.optBoolean("value");
                    break;
                case "manual_mode_fast":
                case "manual_mode":
                case "leaving_home_setting":
                case "at_home_setting":
                case "pause_setting":
                case "holiday_setting":
                    // використовуємо нижче
                    break;
                default:
                    break;
            }
        }

        double manualModeFast = 0, manualMode = 0, leavingHome = 0, atHome = 0, pause = 0, holiday = 0;

        for (int i = 0; i < statusArray.length(); i++) {
            JSONObject s = statusArray.getJSONObject(i);
            String code = s.getString("code");
            Object value = s.get("value");
            if (!(value instanceof Number n)) {
                continue;
            }
            double v = n.doubleValue();
            switch (code) {
                case "manual_mode_fast":
                    manualModeFast = v;
                    break;
                case "manual_mode":
                    manualMode = v;
                    break;
                case "leaving_home_setting":
                    leavingHome = v;
                    break;
                case "at_home_setting":
                    atHome = v;
                    break;
                case "pause_setting":
                    pause = v;
                    break;
                case "holiday_setting":
                    holiday = v;
                    break;
                default:
                    break;
            }
        }

        Double effectiveSetpoint = tempSet != null ? tempSet : 0;

        if ("manual".equals(mode)) {
            if (manualModeFast > 0) {
                effectiveSetpoint = manualModeFast;
            } else if (manualMode > 0) {
                effectiveSetpoint = manualMode;
            }
        } else if ("leaving_home".equals(mode)) {
            effectiveSetpoint = leavingHome;
        } else if ("at_home".equals(mode)) {
            effectiveSetpoint = atHome;
        } else if ("pause".equals(mode)) {
            effectiveSetpoint = pause;
        } else if ("holiday".equals(mode)) {
            effectiveSetpoint = holiday;
        }

        if (mode != null) {
            updateState(CHANNEL_MODE, new StringType(mode));
        }

        if (tempCurrent != null && effectiveSetpoint > 0) {
            updateTemperatureChannel(CHANNEL_DELTA, effectiveSetpoint - tempCurrent);
        }

        if (tempCurrent != null) {
            updateTemperatureChannel(CHANNEL_TEMP_CURRENT, tempCurrent);
        }

        if (tempSet != null) {
            updateTemperatureChannel(CHANNEL_TEMP_SET, tempSet);
        }

        updateTemperatureChannel(CHANNEL_SETPOINT, effectiveSetpoint);
        updateState(CHANNEL_ACTIVE, active ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
    }

    private @Nullable DanfossAllyBridgeHandler getBridgeHandler() {
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
