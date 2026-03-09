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

    // endpoint service properties
    static final String PORT = "aries.rsa.port";
    static final String HOSTNAME = "aries.rsa.hostname";
    static final String BIND_ADDRESS = "aries.rsa.bindAddress";
    static final String ID = "aries.rsa.id";
    static final String THREADS = "aries.rsa.numThreads";
    static final String TIMEOUT = "osgi.basic.timeout";

    // provider component properties
    static final String KEYSTORE = "aries.rsa.keyStore";
    static final String TRUSTSTORE = "aries.rsa.trustStore";
    static final String KEYSTORE_PASSWORD = "aries.rsa.keyStorePassword";
    static final String TRUSTSTORE_PASSWORD = "aries.rsa.trustStorePassword";
    static final String KEY_ALIAS = "aries.rsa.keyAlias";
    static final String MTLS = "aries.rsa.mtls";

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
        return value != null ? value.toString() : defaultValue;
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
}
