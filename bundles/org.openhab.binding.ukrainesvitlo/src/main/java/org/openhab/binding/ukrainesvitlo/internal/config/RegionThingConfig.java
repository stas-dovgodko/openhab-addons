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
package org.openhab.binding.ukrainesvitlo.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Thing configuration for a region schedule.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class RegionThingConfig {
    public @Nullable String region;
    public @Nullable String provider;
    public @Nullable String group;

    public @Nullable String username;
    public @Nullable String password;
    public @Nullable String accountId;

    public @Nullable Integer refreshSeconds;
}
