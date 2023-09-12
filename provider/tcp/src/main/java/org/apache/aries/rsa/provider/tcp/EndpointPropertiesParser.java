/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.rsa.provider.tcp;

import java.util.Map;

import org.osgi.service.remoteserviceadmin.EndpointDescription;

public class EndpointPropertiesParser {
    static final String PORT_KEY = "aries.rsa.port";
    static final String DYNAMIC_PORT = "0";
    static final String HOSTNAME_KEY = "aries.rsa.hostname";
    static final String TIMEOUT_KEY = "osgi.basic.timeout";
    static final String DEFAULT_TIMEOUT_MILLIS = "300000";

    private Map<String, Object> ep;

    public EndpointPropertiesParser(EndpointDescription ep) {
        this.ep = ep.getProperties();
    }

    public EndpointPropertiesParser(Map<String, Object> ep) {
        this.ep = ep;
    }

    public int getTimeoutMillis() {
        return getInt(TIMEOUT_KEY, DEFAULT_TIMEOUT_MILLIS);
    }

    int getInt(String key, String defaultValue) {
        return Integer.parseInt(getString(key, defaultValue));
    }

    String getString(String key, String defaultValue) {
        Object value = ep.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public int getPort() {
        return getInt(PORT_KEY, DYNAMIC_PORT);
    }

    public String getHostname() {
        String hostName = getString(HOSTNAME_KEY, System.getProperty(HOSTNAME_KEY));
        if (hostName == null) {
            hostName = LocalHostUtil.getLocalIp();
        }
        return hostName;
    }

    public int getNumThreads() {
        return getInt("aries.rsa.numThreads", "10");
    }
}
