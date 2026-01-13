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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.ukrainesvitlo.internal.config.RegionThingConfig;
import org.openhab.binding.ukrainesvitlo.internal.model.OutageData;

/**
 * Fetches outage data from a provider backend.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public interface OutageProvider {
    String id();

    OutageData fetch(RegionThingConfig cfg, int timeoutMs) throws Exception;
}
