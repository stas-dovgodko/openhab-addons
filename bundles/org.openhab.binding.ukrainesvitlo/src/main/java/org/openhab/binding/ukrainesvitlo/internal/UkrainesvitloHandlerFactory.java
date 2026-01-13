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
package org.openhab.binding.ukrainesvitlo.internal;

import static org.openhab.binding.ukrainesvitlo.internal.UkrainesvitloBindingConstants.*;

import java.util.HashMap;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.ukrainesvitlo.internal.handler.UkrainesvitloRegionThingHandler;
import org.openhab.binding.ukrainesvitlo.internal.handler.UkrainesvitloServiceBridgeHandler;
import org.openhab.binding.ukrainesvitlo.internal.net.HttpHelper;
import org.openhab.binding.ukrainesvitlo.internal.provider.DtekJsonProvider;
import org.openhab.binding.ukrainesvitlo.internal.provider.ESvitloProvider;
import org.openhab.binding.ukrainesvitlo.internal.provider.OutageProviderFactory;
import org.openhab.binding.ukrainesvitlo.internal.provider.YasnoProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Creates thing and bridge handlers for the binding.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
@Component(service = org.openhab.core.thing.binding.ThingHandlerFactory.class, configurationPid = "binding."
        + BINDING_ID)
public class UkrainesvitloHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(new ThingTypeUID(BINDING_ID, THING_BRIDGE),
            new ThingTypeUID(BINDING_ID, THING_REGION));

    private final HttpClientFactory httpClientFactory;
    private final HttpClient httpClient;
    private final OutageProviderFactory providerFactory;

    @Activate
    public UkrainesvitloHandlerFactory(@Reference HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
        this.httpClient = httpClientFactory.getCommonHttpClient();
        HttpHelper helper = new HttpHelper(this.httpClient);

        var providers = new HashMap<String, org.openhab.binding.ukrainesvitlo.internal.provider.OutageProvider>();
        providers.put("yasno", new YasnoProvider(helper));
        providers.put("dtek_json", new DtekJsonProvider(helper));
        providers.put("e-svitlo", new ESvitloProvider(helper));
        this.providerFactory = new OutageProviderFactory(providers);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID typeUID = thing.getThingTypeUID();
        if (typeUID.equals(new ThingTypeUID(BINDING_ID, THING_BRIDGE)) && thing instanceof Bridge bridge) {
            return new UkrainesvitloServiceBridgeHandler(bridge);
        }
        if (typeUID.equals(new ThingTypeUID(BINDING_ID, THING_REGION))) {
            return new UkrainesvitloRegionThingHandler(thing, providerFactory);
        }
        return null;
    }
}
