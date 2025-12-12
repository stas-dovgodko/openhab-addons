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
package org.openhab.binding.danfossally.internal.thermostat;

import static org.openhab.binding.danfossally.internal.DanfossAllyBindingConstants.*;

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
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

        switch (channelUID.getId()) {
            case CHANNEL_SETPOINT:
                if (command instanceof QuantityType<?> q) {
                    double value = q.toBigDecimal().doubleValue();

                    commands.put("manual_mode_fast", Math.round(value * 10.0));
                    commands.put("mode", "manual");
                }
                break;
            case CHANNEL_MODE:
                if (command instanceof StringType s) {
                    commands.put("mode", s);
                }
                break;
            default:
                break;
        }

        return commands;
    }

    @Override
    protected void status(JSONArray statusArray) {
        // ---- status[] -> map code -> value ----
        Double tempSet = null;
        Double tempCurrent = null;
        Double tempMeasured = null;
        String mode = null;
        boolean active = false;
        boolean fault = false;

        for (int i = 0; i < statusArray.length(); i++) {
            JSONObject s = statusArray.getJSONObject(i);
            String code = s.getString("code");
            Object value = s.get("value");

            switch (code) {
                case "temp_set":
                    if (value instanceof Number n) {
                        tempSet = n.doubleValue();
                    }
                    break;
                case "temp_current":
                    if (value instanceof Number n) {
                        tempCurrent = n.doubleValue();
                    }
                    break;
                case "MeasuredValue":
                    if (value instanceof Number n) {
                        tempMeasured = n.doubleValue();
                    }
                    break;
                case "mode":
                    mode = String.valueOf(value);
                    break;
                case "switch_state":
                    active = s.optBoolean("value");
                    break;
                case "fault":
                    fault = s.optBoolean("value");
                    break;
                case "manual_mode_fast":
                case "manual_mode":
                case "leaving_home_setting":
                case "at_home_setting":
                case "pause_setting":
                case "holiday_setting":
                    // використовуємо нижче
                    break;
                default:
                    break;
            }
        }

        double manualModeFast = 0, manualMode = 0, leavingHome = 0, atHome = 0, pause = 0, holiday = 0;

        for (int i = 0; i < statusArray.length(); i++) {
            JSONObject s = statusArray.getJSONObject(i);
            String code = s.getString("code");
            Object value = s.get("value");
            if (!(value instanceof Number n)) {
                continue;
            }
            double v = n.doubleValue();
            switch (code) {
                case "manual_mode_fast":
                    manualModeFast = v;
                    break;
                case "manual_mode":
                    manualMode = v;
                    break;
                case "leaving_home_setting":
                    leavingHome = v;
                    break;
                case "at_home_setting":
                    atHome = v;
                    break;
                case "pause_setting":
                    pause = v;
                    break;
                case "holiday_setting":
                    holiday = v;
                    break;
                default:
                    break;
            }
        }

        Double effectiveSetpoint = tempSet != null ? tempSet : 0;

        if ("manual".equals(mode)) {
            if (manualModeFast > 0) {
                effectiveSetpoint = manualModeFast;
            } else if (manualMode > 0) {
                effectiveSetpoint = manualMode;
            }
        } else if ("leaving_home".equals(mode)) {
            effectiveSetpoint = leavingHome;
        } else if ("at_home".equals(mode)) {
            effectiveSetpoint = atHome;
        } else if ("pause".equals(mode)) {
            effectiveSetpoint = pause;
        } else if ("holiday".equals(mode)) {
            effectiveSetpoint = holiday;
        }

        if (mode != null) {
            updateState(CHANNEL_MODE, new StringType(mode));
        }

        if (tempCurrent != null && effectiveSetpoint > 0) {
            updateTemperatureChannel(CHANNEL_DELTA, effectiveSetpoint - tempCurrent);
        }

        if (tempCurrent != null) {
            updateTemperatureChannel(CHANNEL_TEMP_CURRENT, tempCurrent);
        }

        if (tempMeasured != null) {
            updateTemperatureChannel(CHANNEL_TEMP_FLOOR, tempMeasured);
        }

        if (tempSet != null) {
            updateTemperatureChannel(CHANNEL_TEMP_SET, tempSet);
        }

        updateTemperatureChannel(CHANNEL_SETPOINT, effectiveSetpoint);
        updateState(CHANNEL_ACTIVE, active ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        updateState(CHANNEL_FAULT, fault ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
    }
}
