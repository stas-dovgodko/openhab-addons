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
package org.openhab.binding.danfossally.internal.gateway;

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;

/**
 * 
 *
 * @author Stas Dovgodko <stas@dovgodko.dev> - Initial contribution
 */
@NonNullByDefault
public class DanfossAllyDeviceHandler extends org.openhab.binding.danfossally.internal.DanfossAllyDeviceHandler {

    public DanfossAllyDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected HashMap<String, Object> command(ChannelUID channelUID, Command command) {
        HashMap<String, Object> commands = new HashMap<String, Object>();

        return commands;
    }

    @Override
    protected void status(JSONArray statusArray) {
        logger.debug("{}", statusArray.toString(2));
    }

    protected void pooling() {
        // nothing to pool
    }
}
