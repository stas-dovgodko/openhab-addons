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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Binding constants.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class UkrainesvitloBindingConstants {
    public static final String BINDING_ID = "ukrainesvitlo";

    public static final String THING_BRIDGE = "service";
    public static final String THING_REGION = "region";

    public static final String CH_ELECTRICITY = "electricity";
    public static final String CH_SCHEDULE_UPDATED_ON = "scheduleUpdatedOn";
    public static final String CH_SCHEDULE_DATA_CHANGED_ON = "scheduleDataChangedOn";
    public static final String CH_NEXT_PLANNED_OUTAGE = "nextPlannedOutage";
    public static final String CH_NEXT_SCHEDULED_OUTAGE = "nextScheduledOutage";
    public static final String CH_NEXT_CONNECTIVITY = "nextConnectivity";
    public static final String CH_PLANNED_JSON = "plannedOutagesJson";
    public static final String CH_SCHEDULED_JSON = "scheduledOutagesJson";
    public static final String CH_DATA_CHANGED = "dataChanged";

    private UkrainesvitloBindingConstants() {
    }
}
