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
package org.openhab.binding.ukrainesvitlo.internal.net;

import java.io.IOException;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;

/**
 * Thin wrapper around Jetty HTTP client.
 *
 * @author stand - Initial contribution
 */
@NonNullByDefault
public class HttpHelper {

    private final HttpClient http;

    public HttpHelper(HttpClient http) {
        this.http = http;
    }

    public String getText(String url, int timeoutMs) throws Exception {
        ContentResponse resp = http.newRequest(url).timeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .send();
        int code = resp.getStatus();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        return resp.getContentAsString();
    }

    public String postForm(String url, Map<String, String> form, int timeoutMs) throws Exception {
        var req = http.POST(url);
        Fields fields = new Fields();
        for (var e : form.entrySet()) {
            fields.add(e.getKey(), e.getValue());
        }
        req.content(new FormContentProvider(fields));
        ContentResponse resp = req.timeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS).send();
        int code = resp.getStatus();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        return resp.getContentAsString();
    }
}
