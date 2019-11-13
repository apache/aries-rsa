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
import java.util.Map;

import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class TcpEndpoint implements Endpoint {
    private EndpointDescription epd;
    private TCPServer tcpServer;
    
    public TcpEndpoint(Object service, Map<String, Object> effectiveProperties) {
        if (service == null) {
            throw new NullPointerException("Service must not be null");
        }
        if (effectiveProperties.get(TCPProvider.TCP_CONFIG_TYPE + ".id") != null) {
            throw new IllegalArgumentException("For the tck .. Just to please you!");
        }
        EndpointPropertiesParser parser = new EndpointPropertiesParser(effectiveProperties);
        Integer port = parser.getPort();
        String hostName = parser.getHostname();
        int numThreads =  parser.getNumThreads();
        tcpServer = new TCPServer(service, hostName, port, numThreads);
        String endpointId = String.format("tcp://%s:%s", hostName, tcpServer.getPort());
        effectiveProperties.put(RemoteConstants.ENDPOINT_ID, endpointId);
        effectiveProperties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "");
        effectiveProperties.put(RemoteConstants.SERVICE_INTENTS, Arrays.asList("osgi.basic", "osgi.async"));
        
        // tck tests for one such property ... so we provide it
        effectiveProperties.put(TCPProvider.TCP_CONFIG_TYPE + ".id", endpointId);
        this.epd = new EndpointDescription(effectiveProperties);
    }

    @Override
    public EndpointDescription description() {
        return this.epd;
    }


    @Override
    public void close() throws IOException {
        tcpServer.close();
    }
}
