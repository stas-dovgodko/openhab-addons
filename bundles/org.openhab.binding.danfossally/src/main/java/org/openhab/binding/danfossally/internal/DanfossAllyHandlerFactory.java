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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;

/**
 * 
 *
 * @author Stas Dovgodko <stas@dovgodko.dev> - Initial contribution
 */
@NonNullByDefault
@Component(service = org.openhab.core.thing.binding.ThingHandlerFactory.class, configurationPid = "binding."
        + BINDING_ID)
public class DanfossAllyHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(BRIDGE_THING_TYPE, THING_TYPE_THERMOSTAT,
            THING_TYPE_GATEWAY, THING_TYPE_CONTROLLER);

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID type = thing.getThingTypeUID();
        if (BRIDGE_THING_TYPE.equals(type)) {
            return new DanfossAllyBridgeHandler((Bridge) thing);
        } else if (THING_TYPE_THERMOSTAT.equals(type)) {
            return new org.openhab.binding.danfossally.internal.thermostat.DanfossAllyDeviceHandler(thing);
        } else if (THING_TYPE_CONTROLLER.equals(type)) {
            return new org.openhab.binding.danfossally.internal.controller.DanfossAllyDeviceHandler(thing);
        } else if (THING_TYPE_GATEWAY.equals(type)) {
            return new org.openhab.binding.danfossally.internal.gateway.DanfossAllyDeviceHandler(thing);
        }

        return null;
    }
}
