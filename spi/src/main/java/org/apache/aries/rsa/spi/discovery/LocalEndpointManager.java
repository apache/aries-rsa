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
package org.apache.aries.rsa.spi.discovery;

import org.osgi.framework.*;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static org.apache.aries.rsa.util.CollectionUtils.union;

/**
 * Keeps track of all local endpoints so that updates about them can be sent to
 * remote systems.
 * <p>
 * This includes registering an EndpointEventListener with a scope that excludes all
 * non-local endpoints, a special service property that uniquely identifies this
 * instance so that the interest manager can exclude it from its tracking, and a
 * ServiceFactory that keeps track of which bundles notified us about which endpoints
 * so that when a bundle is stopped all of its endpoints will be removed, as the spec
 * requires.
 * <p>
 * After being processed here, endpoint events are forwarded to a supplied listener
 * for implementation-specific handling (e.g. transmitting updates to remote hosts).
 */
public class LocalEndpointManager {

    private static final Logger LOG = LoggerFactory.getLogger(LocalEndpointManager.class);

    protected static class EndpointBundles {
        EndpointDescription endpoint;
        Set<Long> bundleIds;

        EndpointBundles(EndpointDescription endpoint, Set<Long> bundleIds) {
            this.endpoint = endpoint;
            this.bundleIds = bundleIds;
        }
    }

    protected ServiceRegistration<EndpointEventListener> listenerRegistration;
    // local endpoint ids and their respective endpoints and the bundles that notified us about them
    protected Map<String, EndpointBundles> localEndpoints = new ConcurrentHashMap<>();
    // the listener to which endpoint events are delegated after being processed
    protected EndpointEventListener listener;

    public Collection<EndpointDescription> getEndpoints() {
        return localEndpoints.values().stream().map(eb -> eb.endpoint).collect(Collectors.toList());
    }

    public void setListener(EndpointEventListener listener) {
        this.listener = listener;
    }

    public void start(BundleContext context, String excludeProperty) {
        listenerRegistration = registerListener(context, excludeProperty);
    }

    public void stop() {
        if (listenerRegistration  != null) {
            listenerRegistration.unregister();
        }
    }

    /**
     * Registers an EndpointEventListener so we get notified of all local endpoints,
     * with a few special features:
     * <ul>
     * <li>It is registered with a special given property that uniquely identifies
     * this instance in order for the interest manager to exclude it in its scope
     * when listening for interests</li>
     * <li>It is registered with a scope that excludes all non-local endpoints
     * by requiring the framework ID to be equal to the given context's framework ID</li>
     * <li>It is registered as a {@link ServiceFactory} in order to keep track of
     * which bundle(s) notifies us of each endpoint, so that when the bundle is
     * stopped we can remove those endpoints (as required by the spec)</li>
     * </ul>
     *
     * @param context the registering bundle context
     * @param excludeProperty the name of a service property that uniquely identifies
     *        this instance
     * @return the listener service registration
     */
    protected ServiceRegistration<EndpointEventListener> registerListener(
            BundleContext context, String excludeProperty) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(excludeProperty, Boolean.TRUE); // mark our own listener for exclusion
        String uuid = context.getProperty(Constants.FRAMEWORK_UUID);
        String scope = String.format("(&(%s=*)(%s=%s))", Constants.OBJECTCLASS,
                RemoteConstants.ENDPOINT_FRAMEWORK_UUID, uuid);
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, scope);
        LOG.debug("registering EndpointEventListener factory with properties {}", props);
        return context.registerService(EndpointEventListener.class, new ListenerServiceFactory(), props);
    }

    /**
     * A {@link ServiceFactory} registered as an {@link EndpointEventListener} so that we can
     * keep track of which bundle(s) notified us about every endpoint.
     */
    protected class ListenerServiceFactory implements ServiceFactory<EndpointEventListener> {

        @Override
        public EndpointEventListener getService(Bundle bundle, ServiceRegistration<EndpointEventListener> registration) {
            long bundleId = bundle.getBundleId();
            LOG.trace("service factory getting EndpointEventListener for bundle {} ({})",
                bundleId, bundle.getSymbolicName());
            return ((event, filter) -> endpointChanged(event, filter, bundleId));
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration<EndpointEventListener> registration, EndpointEventListener service) {
            long bundleId = bundle.getBundleId();
            LOG.trace("service factory ungetting EndpointEventListener for bundle {} ({}) state {}",
                bundleId, bundle.getSymbolicName(), bundle.getState());
            if (bundle.getState() == Bundle.STOPPING) {
                LOG.debug("bundle {} is stopping ({}), removing all of its endpoints",
                    bundleId, bundle.getSymbolicName());
                localEndpoints.values().stream()
                    .filter(eb -> eb.bundleIds.contains(bundleId))
                    .forEach(eb -> endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, eb.endpoint), "", bundleId));
            }
        }
    }

    /**
     * Notification that an endpoint has changed.
     * <p>
     * This method is similar to {@link EndpointEventListener#endpointChanged},
     * with the added bundle ID of the bundle invoking the listener (which
     * we provide by using a ServiceFactory).
     *
     * @param event The event containing the details about the change.
     * @param filter The filter from the {@link EndpointEventListener#ENDPOINT_LISTENER_SCOPE}
     *        that matches (or for {@link EndpointEvent#MODIFIED_ENDMATCH} and
     *        {@link EndpointEvent#REMOVED} used to match) the endpoint, or an empty
     *        string when removing the endpoint because the notifying bundle was stopped
     */
    public void endpointChanged(EndpointEvent event, String filter, Long bundleId) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.debug("got endpointChanged event of type {} from bundle {}: {}",
            event.getType(), bundleId, endpoint);
        switch (event.getType()) {
            case EndpointEvent.ADDED:
            case EndpointEvent.MODIFIED:
                localEndpoints.compute(endpoint.getId(),
                    (e, eb) -> new EndpointBundles(endpoint,
                        eb == null ? singleton(bundleId) : union(eb.bundleIds, singleton(bundleId))));
                break;
            case EndpointEvent.REMOVED:
            case EndpointEvent.MODIFIED_ENDMATCH:
                localEndpoints.computeIfPresent(endpoint.getId(),
                    (e, eb) -> {
                        Set<Long> bundleIds = eb.bundleIds.stream()
                            .filter(id -> !bundleId.equals(id))
                            .collect(Collectors.toSet());
                        return bundleIds.isEmpty() ? null : new EndpointBundles(endpoint, bundleIds);
                    });
                break;
        }
        listener.endpointChanged(event, filter);
    }
}
