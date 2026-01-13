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
package org.openhab.binding.ukrainesvitlo.internal.provider;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Provider-specific endpoints and constants.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class ProviderData {
    public static final String E_SVITLO_BASE = "https://sm.e-svitlo.com.ua/ip_cabinet/restfull_api/improvise/";

    public static final Map<String, java.util.List<String>> DTEK_PROVIDER_URLS = java.util.Collections
            .unmodifiableMap(new java.util.HashMap<>() {
                {
                    put("kyiv_region", java.util.List
                            .of("https://github.com/Baskerville42/outage-data-ua/raw/main/data/kyiv-region.json"));
                    put("dnipro", java.util.List
                            .of("https://github.com/Baskerville42/outage-data-ua/raw/main/data/dnipro.json"));
                    put("odesa", java.util.List
                            .of("https://github.com/Baskerville42/outage-data-ua/raw/main/data/odesa.json"));
                    put("khmelnytskyi", java.util.List.of(
                            "https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Khmelnytskoblenerho.json"));
                    put("ivano_frankivsk", java.util.List.of(
                            "https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Prykarpattiaoblenerho.json"));
                    put("uzhhorod", java.util.List.of(
                            "https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Zakarpattiaoblenerho.json"));
                    put("lviv", java.util.List
                            .of("https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Lvivoblenerho.json"));
                    put("ternopil", java.util.List
                            .of("https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Ternopiloblenerho.json"));
                    put("chernihiv", java.util.List.of(
                            "https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Chernihivoblenergo.json"));
                    put("zaporizhzhia", java.util.List.of(
                            "https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Zaporizhzhiaoblenergo.json"));
                    put("vinnytsia", java.util.List
                            .of("https://github.com/olnet93/gpv-voe-vinnytsia/raw/main/data/Vinnytsiaoblenerho.json"));
                    put("zhytomyr", java.util.List
                            .of("https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Zhytomyroblenergo.json"));
                    put("poltava", java.util.List
                            .of("https://github.com/yaroslav2901/OE_OUTAGE_DATA/raw/main/data/Poltavaoblenergo.json"));
                }
            });

    private ProviderData() {
    }
}
