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
package org.apache.aries.rsa.discovery.tcp;

import org.apache.aries.rsa.annotations.RSADiscoveryProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.osgi.framework.Constants.FRAMEWORK_UUID;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID;

/**
 * The main TCP Discovery provider component.
 * <p>
 * It initializes the provider using config admin configuration,
 * initializes the {@link InterestManager} and {@link TcpConnectionManager},
 * registers an {@link EndpointEventListener} to track locally exported
 * endpoints, and listens for registrations of other
 * EndpointEventListeners which are managed by the InterestManager.
 */
@RSADiscoveryProvider(protocols = "aries.tcp")
@Component(immediate = true, configurationPid = TcpDiscovery.DISCOVERY_TCP_PID)
public class TcpDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(TcpDiscovery.class);
    public static final String DISCOVERY_TCP_PID = "org.apache.aries.rsa.discovery.tcp";
    private static final String OWN_LISTENER_PROP = "aries.discovery.tcp";
    public static final int DEFAULT_PORT = 7667;

    @interface Config {
        String address() default "localhost:" + DEFAULT_PORT;
        String bindAddress() default "0.0.0.0";
        String[] peers() default {};
        long reconnectDelay() default 5000;
        boolean gossip() default true;
    }

    private InterestManager interestManager;
    private TcpConnectionManager connectionManager;
    private ServiceRegistration<?> listenerRegistration;

    public TcpDiscovery() {
        // initialize in constructor before we start getting reference bind events
        interestManager = new InterestManager();
    }

    public static URI toURI(String address) {
        try {
            URI uri = new URI("tcp://" + address);
            if (uri.getPort() == -1) {
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                    DEFAULT_PORT, uri.getPath(), uri.getQuery(), uri.getFragment());
            }
            return uri;
        } catch (URISyntaxException urise) {
            LOG.error("failed to parse address " + address, urise);
            throw new RuntimeException(urise);
        }
    }

    // merge config from ConfigAdmin, framework properties and system properties
    @SuppressWarnings("unchecked")
    private <T extends Annotation> T mergeConfig(BundleContext context, String prefix, T config) {
        Class<T> cls = (Class<T>)config.annotationType();
        return (T)Proxy.newProxyInstance(cls.getClassLoader(), new Class[] { cls },
            (proxy, method, args) -> {
                Object value = method.invoke(config, args);
                Object defaultValue = method.getDefaultValue();
                if (method.getDeclaringClass() != Object.class && Objects.deepEquals(value, defaultValue)) {
                    String prop = prefix + method.getName();
                    value = context.getProperty(prop);
                    if (value == null) {
                        value = System.getProperty(prop);
                    }
                    if (value == null) {
                        value = defaultValue;
                    } else if (method.getReturnType() == Boolean.TYPE) {
                        value = Boolean.valueOf(value.toString());
                    } else if (method.getReturnType() == Integer.TYPE) {
                        value = Integer.valueOf(value.toString());
                    } else if (method.getReturnType() == Long.TYPE) {
                        value = Long.valueOf(value.toString());
                    } else if (method.getReturnType() == String[].class) {
                        value = ((String)value).split("\\s*,\\s*");
                    }
                } else if (method.getDeclaringClass() == Object.class && method.getName().equals("toString")) {
                    // add a nice toString that shows all merged config names and values (including arrays)
                    value = Arrays.stream(cls.getMethods())
                        .filter(m -> !m.getDeclaringClass().equals(Annotation.class)) // not Object.class!
                        .collect(Collectors.<Method, String, Object>toMap(Method::getName, m -> {
                            try {
                                Object v = m.invoke(proxy);
                                return (v == null || v instanceof Object[]) ? Arrays.toString((Object[])v) : v.toString();
                            } catch (Exception ignore) {
                                return "<ERROR>";
                            }
                        })).toString();
                }
                return value;
            });
    }

    private void initConnectionManager(Config config, String uuid) throws IOException {
        String address = config.address();
        String bindAddress = config.bindAddress();
        String[] peers = config.peers();
        if (address == null || address.isEmpty() || address.equals("0.0.0.0")) {
            throw new IllegalArgumentException("invalid address: " + address);
        }
        if (peers.length == 0) {
            // no peers - may be legitimate in a server with incoming connections
            // only, or for single-host dev/itest systems where the bundle should
            // start successfully even if it's not doing anything too useful
            LOG.info("no peers configured - will wait for incoming connections");
        }
        URI uri = toURI(address);
        bindAddress = bindAddress == null ? uri.getHost() : toURI(bindAddress).getHost(); // just the host
        connectionManager = new TcpConnectionManager(
            interestManager, address, uuid, config.reconnectDelay(), config.gossip());
        connectionManager.open(bindAddress, uri.getPort(), Arrays.asList(peers));
    }

    private void registerListener(BundleContext context, String uuid) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(OWN_LISTENER_PROP, Boolean.TRUE); // mark our own listener for exclusion
        String scope = "(&(objectClass=*)(" + ENDPOINT_FRAMEWORK_UUID + "=" + uuid + "))";
        props.put(ENDPOINT_LISTENER_SCOPE, scope);
        listenerRegistration = context.registerService(EndpointEventListener.class, connectionManager, props);
    }

    @Activate
    void start(BundleContext context, Config config) {
        String uuid = context.getProperty(FRAMEWORK_UUID);
        config = mergeConfig(context, getClass().getPackageName() + ".", config);
        LOG.info("Starting TCP discovery for framework {} with config {}", uuid, config);
        try {
            initConnectionManager(config, uuid);
            // register ourselves to capture local endpoint exports
            registerListener(context, uuid);
        } catch (IOException ioe) {
            LOG.error("failed to start TCP connection manager", ioe);
        }
    }

    @Deactivate
    void stop() throws IOException {
        if (listenerRegistration != null) {
            listenerRegistration.unregister();
        }
        if (connectionManager != null) {
            connectionManager.close();
        }
    }

    @Reference(cardinality = MULTIPLE, policy = DYNAMIC, target = "(!(" + OWN_LISTENER_PROP + "=*))")
    void bindEndpointEventListener(ServiceReference<EndpointEventListener> sref, EndpointEventListener listener) {
        interestManager.addListener(sref, listener, true);
    }

    void updatedEndpointEventListener(ServiceReference<EndpointEventListener> sref, EndpointEventListener listener) {
        interestManager.addListener(sref, listener, false);
    }

    void unbindEndpointEventListener(ServiceReference<EndpointEventListener> sref) {
        interestManager.removeListener(sref);
    }
}
