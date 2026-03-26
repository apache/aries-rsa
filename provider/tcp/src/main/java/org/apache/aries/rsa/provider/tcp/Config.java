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
import java.util.UUID;

/**
 * A convenience class for extracting endpoint configuration properties,
 * provider properties and defaults.
 */
public class Config {

    static final String PREFIX = TcpProvider.TCP_CONFIG_TYPE + ".";

    // configuration properties
    static final String
        PORT = PREFIX + "port",
        HOSTNAME = PREFIX + "hostname",
        BIND_ADDRESS = PREFIX + "bindAddress",
        ID = PREFIX + "id",
        THREADS = PREFIX + "numThreads",
        TIMEOUT = "osgi.basic.timeout";

    // provider component properties
    static final String
        KEYSTORE = PREFIX + "keyStore",
        TRUSTSTORE = PREFIX + "trustStore",
        KEYSTORE_PASSWORD = PREFIX + "keyStorePassword",
        TRUSTSTORE_PASSWORD = PREFIX + "trustStorePassword",
        KEY_ALIAS = PREFIX + "keyAlias",
        MTLS = PREFIX + "mtls";

    // endpoint runtime properties
    static final String
        URI = PREFIX + "uri";

    static final int DYNAMIC_PORT = 0;
    static final int DEFAULT_TIMEOUT_MILLIS = 300000;
    static final int DEFAULT_NUM_THREADS = 10;

    private final Map<String, Object> props;
    private final String fallbackId = UUID.randomUUID().toString();

    public Config(Map<String, Object> props) {
        this.props = props;
    }

    public int getTimeoutMillis() {
        return getInt(TIMEOUT, DEFAULT_TIMEOUT_MILLIS);
    }

    int getInt(String key, int defaultValue) {
        Object value = props.get(key);
        return value != null ? Integer.parseInt(value.toString()) : defaultValue;
    }

    String getString(String key, String defaultValue) {
        Object value = props.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + " is not a string");
        }
        return value.toString();
    }

    public int getPort() {
        return getInt(PORT, DYNAMIC_PORT);
    }

    public String getHostname() {
        String hostName = getString(HOSTNAME, System.getProperty(HOSTNAME));
        if (hostName == null) {
            hostName = NetUtil.getLocalIp();
        }
        return hostName;
    }

    public String getBindAddress() {
        return getString(BIND_ADDRESS, System.getProperty(BIND_ADDRESS));
    }

    public String getId() {
        return getString(ID, fallbackId);
    }

    public int getNumThreads() {
        return getInt(THREADS, DEFAULT_NUM_THREADS);
    }

    public String getKeyStore() {
        return getString(KEYSTORE, System.getProperty(KEYSTORE));
    }

    public String getTrustStore() {
        return getString(TRUSTSTORE, System.getProperty(TRUSTSTORE));
    }

    public String getKeyStorePassword() {
        return getString(KEYSTORE_PASSWORD, System.getProperty(KEYSTORE_PASSWORD));
    }

    public String getTrustStorePassword() {
        return getString(TRUSTSTORE_PASSWORD, System.getProperty(TRUSTSTORE_PASSWORD));
    }

    public String getKeyAlias() {
        return getString(KEY_ALIAS, System.getProperty(KEY_ALIAS));
    }

    public boolean isMtls() {
        return Boolean.parseBoolean(getString(MTLS, System.getProperty(MTLS)));
    }

    public String getUri() {
        return getString(URI, null);
    }
}
