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
package org.openhab.binding.ukrainesvitlo.internal.model;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Container for outage data and derived timestamps.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class OutageData {
    public @Nullable String electricity; // connected | planned_outage | emergency

    public @Nullable ZonedDateTime scheduleUpdatedOn;
    public @Nullable ZonedDateTime scheduleDataChangedOn;

    public @Nullable ZonedDateTime nextPlannedOutage;
    public @Nullable ZonedDateTime nextScheduledOutage;
    public @Nullable ZonedDateTime nextConnectivity;

    public List<OutageEvent> plannedEvents = new ArrayList<>();
    public List<OutageEvent> scheduledEvents = new ArrayList<>();
}
