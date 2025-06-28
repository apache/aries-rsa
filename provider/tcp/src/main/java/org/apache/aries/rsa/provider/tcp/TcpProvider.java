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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.aries.rsa.annotations.RSADistributionProvider;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.spi.IntentUnsatisfiedException;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main TCP distribution provider class, which can import remote endpoints
 * (by creating a local proxy that sends method invocations to the remote
 * services via a TcpInvocationHandler) and export local services (by adding the
 * service to a TcpServer that can handle client connections and dispatch the
 * method invocations to the service instance.)
 */
@SuppressWarnings("rawtypes")
@RSADistributionProvider(configs="aries.tcp")
@Component(property = { //
        RemoteConstants.REMOTE_INTENTS_SUPPORTED + "=osgi.basic",
        RemoteConstants.REMOTE_INTENTS_SUPPORTED + "=osgi.async",
        RemoteConstants.REMOTE_CONFIGS_SUPPORTED + "=" + TcpProvider.TCP_CONFIG_TYPE //
})
public class TcpProvider implements DistributionProvider {
    static final String TCP_CONFIG_TYPE = "aries.tcp";
    private static final String[] SUPPORTED_INTENTS = { "osgi.basic", "osgi.async"};

    private Logger logger = LoggerFactory.getLogger(TcpProvider.class);

    private Map<Integer, TcpServer> servers = new HashMap<>();

    @Override
    public String[] getSupportedTypes() {
        return new String[] {TCP_CONFIG_TYPE};
    }

    @SafeVarargs
    private static <T> Set<T> union(Collection<T>... collections) {
        Set<T> union = new HashSet<>();
        for (Collection<T> c : collections)
            if (c != null)
                union.addAll(c);
        return union;
    }

    @Override
    public Endpoint exportService(Object serviceO,
                                  BundleContext serviceContext,
                                  Map<String, Object> effectiveProperties,
                                  Class[] exportedInterfaces) {
        effectiveProperties.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, getSupportedTypes());
        Set<String> intents = union(
            StringPlus.normalize(effectiveProperties.get(RemoteConstants.SERVICE_EXPORTED_INTENTS)),
            StringPlus.normalize(effectiveProperties.get(RemoteConstants.SERVICE_EXPORTED_INTENTS_EXTRA)));
        intents.removeAll(Arrays.asList(SUPPORTED_INTENTS));
        if (!intents.isEmpty()) {
            logger.warn("Unsupported intents found: {}. Not exporting service", intents);
            return null;
        }
        TcpEndpoint endpoint = new TcpEndpoint(serviceO, effectiveProperties, this::removeServer);
        addServer(serviceO, endpoint);
        return endpoint;
    }

    private synchronized void addServer(Object serviceO, TcpEndpoint endpoint) {
        // port 0 means dynamically allocated free port
        int port = endpoint.getPort();
        TcpServer server = servers.get(port);
        if (server == null || port == 0) {
            server = new TcpServer(endpoint.getBindAddress(), port, endpoint.getNumThreads());
            port = server.getPort(); // get the real port
            endpoint.setPort(port);
            servers.put(port, server);
        }
        // different services may configure different number of threads - we pick the max
        if (endpoint.getNumThreads() > server.getNumThreads()) {
            server.setNumThreads(endpoint.getNumThreads());
        }
        server.addService(endpoint.description(), serviceO);
    }

    private synchronized void removeServer(TcpEndpoint endpoint) {
        TcpServer server = servers.get(endpoint.getPort());
        server.removeService(endpoint.description().getId());
        if (server.isEmpty()) {
            try {
                servers.remove(endpoint.getPort()).close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    @Override
    public Object importEndpoint(ClassLoader cl,
                                 BundleContext consumerContext,
                                 Class[] interfaces,
                                 EndpointDescription endpoint)
        throws IntentUnsatisfiedException {
        try {
            String endpointId = endpoint.getId();
            URI address = new URI(endpointId);
            int timeout = new EndpointPropertiesParser(endpoint).getTimeoutMillis();
            InvocationHandler handler = new TcpInvocationHandler(cl, address.getHost(), address.getPort(), endpointId, timeout);
            return Proxy.newProxyInstance(cl, interfaces, handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
