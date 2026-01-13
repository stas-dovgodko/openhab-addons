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
 * Provider registry.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class OutageProviderFactory {
    private final Map<String, OutageProvider> providers;

    public OutageProviderFactory(Map<String, OutageProvider> providers) {
        this.providers = providers;
    }

    public OutageProvider get(String id) {
        OutageProvider p = providers.get(id);
        if (p == null) {
            throw new IllegalArgumentException("Unknown provider: " + id);
        }
        return p;
    }
}
