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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingTypeUID;

/**
 * 
 *
 * @author Stas Dovgodko <stas@dovgodko.dev> - Initial contribution
 */
@NonNullByDefault
public class DanfossAllyBindingConstants {

    public static final String BINDING_ID = "danfossally";

    // Bridge
    public static final ThingTypeUID BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, "account");

    // Один тип thing для термостата
    public static final ThingTypeUID THING_TYPE_THERMOSTAT = new ThingTypeUID(BINDING_ID, "thermostat");

    // Канали (мінімальний набір, можна розширювати)
    public static final String CHANNEL_ONLINE = "online";
    public static final String CHANNEL_SUB = "sub";
    public static final String CHANNEL_ACTIVE_TIME = "activeTime";
    public static final String CHANNEL_CREATE_TIME = "createTime";
    public static final String CHANNEL_UPDATE_TIME = "updateTime";

    public static final String CHANNEL_TEMP_CURRENT = "tempCurrent";
    public static final String CHANNEL_TEMP_SET = "tempSet";
    public static final String CHANNEL_MODE = "mode";
    public static final String CHANNEL_SETPOINT = "setpoint";
    public static final String CHANNEL_DELTA = "delta";
    public static final String CHANNEL_ACTIVE = "active";

    public static ChannelUID channelUID(String thingUID, String channelId) {
        return new ChannelUID(thingUID + ":" + channelId);
    }

    // Константа для конфіг-параметра у thing-types.xml
    public static final String CONFIG_DEVICE_ID = "deviceId";

    // Набір типів, які може знаходити discovery
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_THERMOSTAT);

    // Thermostat device types from API
    public static final Set<String> THERMOSTAT_DEVICE_TYPES = Set.of("Danfoss Icon2 RT");
}
