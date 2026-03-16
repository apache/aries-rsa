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
package org.apache.aries.rsa.discovery.config;

import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class ConfigDiscovery implements ManagedServiceFactory {
    private final Map<String, EndpointDescription> endpoints = new ConcurrentHashMap<>();
    private final Map<EndpointEventListener, Collection<Filter>> listenerToFilters = new HashMap<>();

    @Override
    public String getName() {
        return "Aries RSA Config Discovery";
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
        addDeclaredRemoteService(pid, properties);
    }

    @Override
    public void deleted(String pid) {
        removeServiceDeclaredInConfig(pid);
    }

    private static List<Filter> createFilters(ServiceReference<EndpointEventListener> ref) {
        List<String> values = StringPlus.normalize(ref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE));
        List<Filter> filters = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                filters.add(FrameworkUtil.createFilter(value));
            } catch (InvalidSyntaxException ignore) { // bad filter never matches
            }
        }
        return filters;
    }

    private static boolean matches(Filter filter, EndpointDescription endpoint) {
        return filter.match(new Hashtable<>(endpoint.getProperties())); // don't use matches() which is case-sensitive
    }

    void addListener(ServiceReference<EndpointEventListener> ref, EndpointEventListener listener) {
        List<Filter> filters = createFilters(ref);
        if (!filters.isEmpty()) {
            synchronized (listenerToFilters) {
                listenerToFilters.put(listener, filters);
            }
            triggerCallbacks(filters, listener);
        }
    }

    void removeListener(EndpointEventListener listener) {
        synchronized (listenerToFilters) {
            listenerToFilters.remove(listener);
        }
    }

    @SuppressWarnings("rawtypes")
    private void addDeclaredRemoteService(String pid, Dictionary config) {
        EndpointDescription endpoint = new EndpointDescription(PropertyValidator.validate(config));
        endpoints.put(pid, endpoint);
        triggerCallbacks(new EndpointEvent(EndpointEvent.ADDED, endpoint));
    }

    private void removeServiceDeclaredInConfig(String pid) {
        EndpointDescription endpoint = endpoints.remove(pid);
        if (endpoint != null) {
            triggerCallbacks(new EndpointEvent(EndpointEvent.REMOVED, endpoint));
        }
    }

    private void triggerCallbacks(EndpointEvent event) {
        EndpointDescription endpoint = event.getEndpoint();
        // make a copy of matched filters/listeners so that caller doesn't need to hold locks while triggering events
        List<Map.Entry<EndpointEventListener, Filter>> matched = new ArrayList<>();
        synchronized (listenerToFilters) {
            for (Map.Entry<EndpointEventListener, Collection<Filter>> entry : listenerToFilters.entrySet()) {
                EndpointEventListener listener = entry.getKey();
                for (Filter filter : entry.getValue()) {
                    if (matches(filter, endpoint)) {
                        matched.add(Map.entry(listener, filter));
                    }
                }
            }
        }
        // then trigger events without a lock
        for (Map.Entry<EndpointEventListener, Filter> entry : matched) {
            entry.getKey().endpointChanged(event, entry.getValue().toString());
        }
    }

    private void triggerCallbacks(EndpointEventListener endpointListener, Filter filter, EndpointEvent event) {
        if (matches(filter, event.getEndpoint())) {
            endpointListener.endpointChanged(event, filter.toString());
        }
    }

    private void triggerCallbacks(Collection<Filter> filters, EndpointEventListener endpointListener) {
        for (Filter filter : filters) {
            for (EndpointDescription endpoint : endpoints.values()) {
                EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
                triggerCallbacks(endpointListener, filter, event);
            }
        }
    }
}
