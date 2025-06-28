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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

/**
 * Contains the details of a TcpProvider's Endpoint (exported service).
 */
public class TcpEndpoint implements Endpoint {

    private String hostname;
    private String bindAddress;
    private int port;
    private int numThreads;
    private Consumer<TcpEndpoint> closeCallback;

    private EndpointDescription epd;

    public TcpEndpoint(Object service, Map<String, Object> effectiveProperties, Consumer<TcpEndpoint> closeCallback) {
        if (service == null) {
            throw new NullPointerException("Service must not be null");
        }
        if (effectiveProperties.get(TcpProvider.TCP_CONFIG_TYPE + ".id") != null) {
            throw new IllegalArgumentException("For the tck .. Just to please you!");
        }
        this.closeCallback = closeCallback;
        EndpointPropertiesParser parser = new EndpointPropertiesParser(effectiveProperties);
        port = parser.getPort(); // this may initially be 0 for dynamic port
        hostname = parser.getHostname();
        bindAddress = parser.getBindAddress();
        numThreads =  parser.getNumThreads();
        updateEndpointDescription(effectiveProperties);
    }

    private void updateEndpointDescription(Map<String, Object> effectiveProperties) {
        effectiveProperties = new HashMap<>(effectiveProperties);
        EndpointPropertiesParser parser = new EndpointPropertiesParser(effectiveProperties);
        String endpointId = String.format("tcp://%s:%s/%s", hostname, port, parser.getId());
        effectiveProperties.put(RemoteConstants.ENDPOINT_ID, endpointId);
        effectiveProperties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "");
        effectiveProperties.put(RemoteConstants.SERVICE_INTENTS, Arrays.asList("osgi.basic", "osgi.async"));

        // tck tests for one such property ... so we provide it
        effectiveProperties.put(TcpProvider.TCP_CONFIG_TYPE + ".id", endpointId);
        this.epd = new EndpointDescription(effectiveProperties);
    }

    public String getHostname() {
        return hostname;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        if (this.port == port)
            return;
        this.port = port;
        updateEndpointDescription(epd.getProperties());
    }

    public int getNumThreads() {
        return numThreads;
    }

    @Override
    public EndpointDescription description() {
        return this.epd;
    }

    @Override
    public void close() throws IOException {
        if (closeCallback != null)
            closeCallback.accept(this);
    }
}
