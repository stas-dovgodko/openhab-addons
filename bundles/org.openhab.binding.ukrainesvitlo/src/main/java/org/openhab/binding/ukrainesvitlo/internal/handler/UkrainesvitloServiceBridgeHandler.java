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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.ukrainesvitlo.internal.config.ServiceBridgeConfig;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;

/**
 * Bridge handler for shared service settings.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class UkrainesvitloServiceBridgeHandler extends BaseBridgeHandler {

    public UkrainesvitloServiceBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        // No active work; used to store defaults.
        updateStatus(org.openhab.core.thing.ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No writable channels; ignore commands.
    }

    public ServiceBridgeConfig getBridgeConfig() {
        return getConfigAs(ServiceBridgeConfig.class);
    }
}
