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
package org.apache.aries.rsa.provider.fastbin;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.io.ClientInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.tcp.ClientInvokerImpl;
import org.apache.aries.rsa.provider.fastbin.tcp.ServerInvokerImpl;
import org.apache.aries.rsa.provider.fastbin.util.UuidGenerator;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.spi.IntentUnsatisfiedException;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class FastBinProvider implements DistributionProvider {

	private static final Logger LOG = LoggerFactory.getLogger(FastBinProvider.class);
	
    public static final String FASTBIN_CONFIG_TYPE = "aries.fastbin";

    public static final String FASTBIN_ADDRESS = FASTBIN_CONFIG_TYPE + ".address";

    private final String uri;
    private final String exportedAddress;
    private final long timeout;

    private final DispatchQueue queue = Dispatch.createQueue();
    private final Map<String, SerializationStrategy> serializationStrategies = new ConcurrentHashMap<>();

    private ClientInvoker client;
    private ServerInvoker server;

    public FastBinProvider(java.lang.String uri, java.lang.String exportedAddress, long timeout) throws Exception {
        this.uri = uri;
        this.exportedAddress = exportedAddress;
        this.timeout = timeout;
        // Create client and server
        this.client = new ClientInvokerImpl(queue, timeout, serializationStrategies);
        this.server = new ServerInvokerImpl(uri, queue, serializationStrategies);
        this.client.start();
        this.server.start();
    }

    public void close() {
    	client.stop();
    	final Semaphore counter = new Semaphore(0);
    	server.stop(() -> {
    		counter.release(1);
    	});
        try {
        	if(!counter.tryAcquire(1, 30, TimeUnit.SECONDS)) {
        		LOG.warn("Server/Client failed to shut down in time. Proceeding shutdown anyway...");
        	}
        } catch(InterruptedException e) {
        	LOG.warn("Interrupted while waiting for Server/Client shutdown");
        }
    }

    public ClientInvoker getClient() {
        return client;
    }

    public ServerInvoker getServer() {
        return server;
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[] {FASTBIN_CONFIG_TYPE};
    }

    @Override
    public Endpoint exportService(final Object serviceO,
                                  BundleContext serviceContext,
                                  Map<String, Object> effectiveProperties,
                                  Class[] exportedInterfaces) {

        // Compute properties
        /*
        Map<String, Object> properties = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        for (String k : reference.getPropertyKeys()) {
            properties.put(k, reference.getProperty(k));
        }
        // Bail out if there is any intents specified, we don't support any
        Set<String> intents = Utils.normalize(properties.get(SERVICE_EXPORTED_INTENTS));
        Set<String> extraIntents = Utils.normalize(properties.get(SERVICE_EXPORTED_INTENTS_EXTRA));
        if (!intents.isEmpty() || !extraIntents.isEmpty()) {
            throw new UnsupportedOperationException();
        }
        // Bail out if there are any configurations specified, we don't support any
        Set<String> configs = Utils.normalize(properties.get(SERVICE_EXPORTED_CONFIGS));
        if (configs.isEmpty()) {
            configs.add(CONFIG);
        } else if (!configs.contains(CONFIG)) {
            throw new UnsupportedOperationException();
        }

        URI connectUri = new URI(this.server.getConnectAddress());
        String fabricAddress = connectUri.getScheme() + "://" + exportedAddress + ":" + connectUri.getPort();

        properties.remove(SERVICE_EXPORTED_CONFIGS);
        properties.put(SERVICE_IMPORTED_CONFIGS, new String[] { CONFIG });
        properties.put(ENDPOINT_FRAMEWORK_UUID, this.uuid);
        properties.put(FABRIC_ADDRESS, fabricAddress);

        String uuid = UuidGenerator.getUUID();
        properties.put(ENDPOINT_ID, uuid);
        */

        String endpointId = UuidGenerator.getUUID();
        effectiveProperties.put(RemoteConstants.ENDPOINT_ID, endpointId);

        URI connectUri = URI.create(this.server.getConnectAddress());
        String fastbinAddress = connectUri.getScheme() + "://" + exportedAddress + ":" + connectUri.getPort();
        effectiveProperties.put(FASTBIN_ADDRESS, fastbinAddress);
        effectiveProperties.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, getSupportedTypes());

        // Now, export the service
        final EndpointDescription description = new EndpointDescription(effectiveProperties);

        // Export it
        server.registerService(description.getId(), new ServerInvoker.ServiceFactory() {
            public Object get() {
                return serviceO;
            }
            public void unget() {
            }
        }, serviceO.getClass().getClassLoader());

        return new Endpoint() {
            @Override
            public EndpointDescription description() {
                return description;
            }

            @Override
            public void close() throws IOException {
                server.unregisterService(description.getId());
            }
        };
    }

    @Override
    public Object importEndpoint(ClassLoader cl,
                                 BundleContext consumerContext,
                                 Class[] interfaces,
                                 EndpointDescription endpoint)
            throws IntentUnsatisfiedException {

        String address = (String) endpoint.getProperties().get(FASTBIN_ADDRESS);
        InvocationHandler handler = client.getProxy(address, endpoint.getId(), cl);
        return Proxy.newProxyInstance(cl, interfaces, handler);
    }

}
