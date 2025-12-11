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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.rmi.UnexpectedException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.openhab.core.io.net.http.HttpUtil;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
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
public class DanfossAllyBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(DanfossAllyBridgeHandler.class);

    private @Nullable DanfossAllyBridgeConfiguration config;
    private @Nullable ScheduledFuture<?> pollingJob;

    private @Nullable String accessToken;
    private long tokenExpiresAt = 0L;

    public DanfossAllyBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        DanfossAllyBridgeConfiguration cfg = getConfigAs(DanfossAllyBridgeConfiguration.class);

        if (cfg.clientId.isEmpty() || cfg.clientSecret.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "ClientId/ClientSecret not set");
            return;
        }

        this.config = cfg;

        int interval = cfg.pollingInterval > 0 ? cfg.pollingInterval : 60;

        pollingJob = scheduler.scheduleWithFixedDelay(() -> {
            try {
                JSONArray devices = getDevices();
                if (devices == null) {
                    throw new UnexpectedException("No devices found");
                }

                Set<String> foundIds = new HashSet<>();

                for (int i = 0; i < devices.length(); i++) {
                    JSONObject dev = devices.getJSONObject(i);
                    String id = dev.getString("id");
                    String name = dev.optString("name", "Danfoss Ally " + id);
                    String deviceType = dev.getString("device_type");

                    if (THERMOSTAT_DEVICE_TYPES.contains(deviceType)) {

                        ThingUID thingUID = new ThingUID(THING_TYPE_THERMOSTAT, getThing().getUID(), id);

                        getThing().getThings().forEach(child -> {
                            if (child
                                    .getHandler() instanceof org.openhab.binding.danfossally.internal.thermostat.DanfossAllyDeviceHandler handler) {
                                if (handler.getDeviceId().equals(id)) {
                                    handler.updateFromDevice(dev);

                                    logger.debug("Found thermostat device [{}, {}]", id, dev.toString(2));

                                    foundIds.add(id);
                                }
                            } else {
                                // ?
                                logger.error("Wrong device handler [{}, {}]", id,
                                        child.getHandler().getClass().getCanonicalName());
                            }
                        });

                        logger.debug("Ally [{}, {}]", thingUID.getAsString(), name);
                    }
                }

                for (Thing child : getThing().getThings()) {
                    if (child
                            .getHandler() instanceof org.openhab.binding.danfossally.internal.thermostat.DanfossAllyDeviceHandler handler) {
                        String childId = handler.getDeviceId();
                        if (!foundIds.contains(childId)) {
                            logger.warn("Danfoss Ally: Device {} not returned by API → marking OFFLINE", childId);
                            child.setStatusInfo(new ThingStatusInfo(ThingStatus.UNINITIALIZED, ThingStatusDetail.NONE,
                                    "Device not present in Danfoss Ally API response"));
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error while polling Danfoss Ally devices", e);
            }
        }, 5, interval, TimeUnit.SECONDS);

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        accessToken = null;
    }

    public synchronized @Nullable String getAccessToken() {
        DanfossAllyBridgeConfiguration cfg = config;
        if (cfg == null) {
            return null;
        }

        long now = Instant.now().toEpochMilli();
        if (accessToken != null && now < tokenExpiresAt) {
            return accessToken;
        }

        try {
            String authPayload = cfg.clientId + ":" + cfg.clientSecret;
            String authHeader = "Basic "
                    + Base64.getEncoder().encodeToString(authPayload.getBytes(StandardCharsets.UTF_8));

            String body = "grant_type=client_credentials";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

            Properties headers = new Properties();
            headers.put("Authorization", authHeader);
            headers.put("Accept", "application/json");

            String response = HttpUtil.executeUrl("POST", "https://api.danfoss.com/oauth2/token", headers,
                    new ByteArrayInputStream(bytes), "application/x-www-form-urlencoded;charset=UTF-8", 5000);

            JSONObject json = new JSONObject(new JSONTokener(response));
            String token = json.getString("access_token");
            int expiresIn = json.optInt("expires_in", 1800);

            accessToken = token;
            tokenExpiresAt = now + (long) (expiresIn - 60) * 1000L;

            return accessToken;
        } catch (IOException e) {
            logger.warn("Error getting Danfoss Ally token: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return null;
        }
    }

    public @Nullable JSONArray getDevices() {
        String token = getAccessToken();
        if (token == null) {
            return null;
        }

        try {
            String url = "https://api.danfoss.com/ally/devices";

            Properties headers = new Properties();
            headers.put("Authorization", "Bearer " + token);
            headers.put("Accept", "application/json");

            String response = HttpUtil.executeUrl("GET", url, headers, null, // content (InputStream) – не потрібно для
                                                                             // GET
                    null, // contentType
                    5000 // timeout
            );

            JSONObject json = new JSONObject(new JSONTokener(response));
            return json.getJSONArray("result");
        } catch (IOException e) {
            logger.warn("Error getting Danfoss Ally devices: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return null;
        }
    }

    public @Nullable JSONObject getDevice(String deviceId) {
        String token = getAccessToken();
        if (token == null) {
            return null;
        }

        try {
            String url = "https://api.danfoss.com/ally/devices/" + deviceId;

            Properties headers = new Properties();
            headers.put("Authorization", "Bearer " + token);
            headers.put("Accept", "application/json");

            String response = HttpUtil.executeUrl("GET", url, headers, null, null, 5000);

            JSONObject json = new JSONObject(new JSONTokener(response));
            return json.getJSONObject("result");
        } catch (IOException e) {
            logger.warn("Error getting Danfoss Ally device {}: {}", deviceId, e.getMessage());
            return null;
        }
    }

    public boolean sendCommands(String deviceId, JSONArray commands) {
        String token = getAccessToken();
        if (token == null) {
            return false;
        }

        try {
            String url = "https://api.danfoss.com/ally/devices/" + deviceId + "/commands";

            JSONObject payload = new JSONObject();
            payload.put("commands", commands);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            Properties headers = new Properties();
            headers.put("Authorization", "Bearer " + token);
            headers.put("Accept", "application/json");

            String response = HttpUtil.executeUrl("POST", url, headers, new ByteArrayInputStream(bytes),
                    "application/json", 5000);

            JSONObject json = new JSONObject(new JSONTokener(response));

            return json.optBoolean("result", true);
        } catch (IOException e) {
            logger.warn("Error sending commands to device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            /*
             * try {
             * getThing().getThings().forEach(child -> {
             * if (child.getHandler() instanceof DanfossAllyDeviceHandler handler) {
             * // poll
             * }
             * });
             * } catch (Exception e) {
             * logger.warn("Error while refreshing Danfoss Ally devices via bridge command", e);
             * }
             */
        }
    }

    @Override
    public java.util.Collection<Class<? extends ThingHandlerService>> getServices() {
        return java.util.Set.of(org.openhab.binding.danfossally.internal.discovery.DanfossAllyDiscoveryService.class);
    }
}
