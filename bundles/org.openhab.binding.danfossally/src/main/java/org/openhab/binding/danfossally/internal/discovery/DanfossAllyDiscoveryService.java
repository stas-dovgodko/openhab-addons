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
package org.openhab.binding.danfossally.internal.discovery;

import static org.openhab.binding.danfossally.internal.DanfossAllyBindingConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.danfossally.internal.DanfossAllyBridgeHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery service for Danfoss Ally thermostats (per bridge).
 * 
 * @author Stas Dovgodko <stas@dovgodko.dev> - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerService.class, //
        scope = ServiceScope.PROTOTYPE, //
        configurationPid = "discovery.danfossally")
public class DanfossAllyDiscoveryService extends AbstractThingHandlerDiscoveryService<DanfossAllyBridgeHandler> {

    private static final Logger logger = LoggerFactory.getLogger(DanfossAllyDiscoveryService.class);

    private static final int SCAN_TIMEOUT_SECONDS = 60;
    private static final Set<ThingTypeUID> SUPPORTED_TYPES = Set.of(THING_TYPE_THERMOSTAT);

    // буде виставлений фреймворком через setThingHandler()
    private @Nullable DanfossAllyBridgeHandler bridgeHandler;

    public DanfossAllyDiscoveryService() throws IllegalArgumentException {
        // thingClazz, supportedThingTypes, timeout, backgroundDiscoveryEnabled
        super(DanfossAllyBridgeHandler.class, SUPPORTED_TYPES, SCAN_TIMEOUT_SECONDS, true);
    }

    @Override
    public void setThingHandler(org.openhab.core.thing.binding.ThingHandler handler) {
        super.setThingHandler(handler);
        if (handler instanceof DanfossAllyBridgeHandler allyBridge) {
            this.bridgeHandler = allyBridge;
        } else {
            this.bridgeHandler = null;
        }
    }

    @Override
    protected void startScan() {
        logger.debug("Starting Danfoss Ally device scan");
        discoverDevicesForBridge();
    }

    private void discoverDevicesForBridge() {
        DanfossAllyBridgeHandler bridge = this.bridgeHandler;
        if (bridge == null) {
            logger.debug("No DanfossAllyBridgeHandler attached to discovery service");
            return;
        }

        if (bridge.getThing().getStatus() != ThingStatus.ONLINE) {
            logger.debug("Bridge {} is not ONLINE (status = {}), skipping discovery", bridge.getThing().getUID(),
                    bridge.getThing().getStatus());
            return;
        }

        @Nullable
        JSONArray devices = bridge.getDevices();
        if (devices == null) {
            logger.debug("Bridge {} returned null device list", bridge.getThing().getUID());
            return;
        }

        ThingUID bridgeUID = bridge.getThing().getUID();

        for (int i = 0; i < devices.length(); i++) {
            JSONObject dev = devices.getJSONObject(i);

            String id = dev.getString("id");
            String name = dev.optString("name", "Danfoss Ally " + id);
            String deviceType = dev.getString("device_type");

            if (THERMOSTAT_DEVICE_TYPES.contains(deviceType)) {
                ThingUID thingUID = new ThingUID(THING_TYPE_THERMOSTAT, bridgeUID, id);

                Map<String, Object> props = new HashMap<>();
                props.put(CONFIG_DEVICE_ID, id);

                DiscoveryResult result = DiscoveryResultBuilder.create(thingUID) //
                        .withThingType(THING_TYPE_THERMOSTAT) //
                        .withBridge(bridgeUID) //
                        .withLabel(name) //
                        .withProperties(props) //
                        .build();

                logger.debug("Discovered Danfoss Ally thermostat: id={}, name={}, uid={}", id, name, thingUID);
                thingDiscovered(result);
            } else {
                logger.debug("Discovered unsupported Ally device: id={}, name={}, type={}", id, name, deviceType);
            }
        }
    }
}
