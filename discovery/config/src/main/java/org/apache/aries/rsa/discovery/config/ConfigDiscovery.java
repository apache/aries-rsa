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
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class ConfigDiscovery implements ManagedServiceFactory {
    private final Map<EndpointDescription, String> endpointDescriptions = new ConcurrentHashMap<>();
    private final Map<EndpointEventListener, Collection<String>> listenerToFilters = new HashMap<>();
    private final Map<String, Collection<EndpointEventListener>> filterToListeners = new HashMap<>();

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

    void addListener(ServiceReference<EndpointEventListener> endpointListenerRef, EndpointEventListener endpointListener) {
        List<String> filters = StringPlus.normalize(endpointListenerRef.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE));
        if (filters.isEmpty()) {
            return;
        }

        synchronized (listenerToFilters) {
            listenerToFilters.put(endpointListener, filters);
            for (String filter : filters) {
                Collection<EndpointEventListener> listeners = filterToListeners.get(filter);
                if (listeners == null) {
                    listeners = new ArrayList<>();
                    filterToListeners.put(filter, listeners);
                }
                listeners.add(endpointListener);
            }
        }

        triggerCallbacks(filters, endpointListener);
    }

    void removeListener(EndpointEventListener endpointListener) {
        synchronized (listenerToFilters) {
            Collection<String> filters = listenerToFilters.remove(endpointListener);
            if (filters == null) {
                return;
            }

            for (String filter : filters) {
                Collection<EndpointEventListener> listeners = filterToListeners.get(filter);
                if (listeners != null) {
                    listeners.remove(endpointListener);
                    if (listeners.isEmpty()) {
                        filterToListeners.remove(filter);
                    }
                }
            }
        }
    }

    private Map<String, Collection<EndpointEventListener>> getMatchingListeners(EndpointDescription endpoint) {
        // return a copy of matched filters/listeners so that caller doesn't need to hold locks while triggering events
        Map<String, Collection<EndpointEventListener>> matched = new HashMap<>();
        synchronized (listenerToFilters) {
            for (Map.Entry<String, Collection<EndpointEventListener>> entry : filterToListeners.entrySet()) {
                String filter = entry.getKey();
                if (matchFilter(filter, endpoint)) {
                    matched.put(filter, new ArrayList<>(entry.getValue()));
                }
            }
        }
        return matched;
    }

    @SuppressWarnings("rawtypes")
    private void addDeclaredRemoteService(String pid, Dictionary config) {
        EndpointDescription endpoint = new EndpointDescription(PropertyValidator.validate(config));
        endpointDescriptions.put(endpoint, pid);
        EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
        triggerCallbacks(event);
    }

    private void removeServiceDeclaredInConfig(String pid) {
        for (Iterator<Map.Entry<EndpointDescription, String>> i = endpointDescriptions.entrySet().iterator();
             i.hasNext(); ) {
            Map.Entry<EndpointDescription, String> entry = i.next();
            if (pid.equals(entry.getValue())) {
                EndpointEvent event = new EndpointEvent(EndpointEvent.REMOVED, entry.getKey());
                triggerCallbacks(event);
                i.remove();
            }
        }
    }

    private void triggerCallbacks(EndpointEvent event) {
        EndpointDescription endpoint = event.getEndpoint();
        for (Map.Entry<String, Collection<EndpointEventListener>> entry : getMatchingListeners(endpoint).entrySet()) {
            String filter = entry.getKey();
            for (EndpointEventListener listener : entry.getValue()) {
                triggerCallbacks(listener, filter, event);
            }
        }
    }

    private void triggerCallbacks(EndpointEventListener endpointListener, String filter,
                                  EndpointEvent event) {
        if (!matchFilter(filter, event.getEndpoint())) {
            return;
        }

        endpointListener.endpointChanged(event, filter);
    }

    private void triggerCallbacks(Collection<String> filters, EndpointEventListener endpointListener) {
        for (String filter : filters) {
            for (EndpointDescription endpoint : endpointDescriptions.keySet()) {
                EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
                triggerCallbacks(endpointListener, filter, event);
            }
        }
    }

    private static boolean matchFilter(String filter, EndpointDescription endpoint) {
        if (filter == null) {
            return false;
        }

        try {
            Filter f = FrameworkUtil.createFilter(filter);
            Dictionary<String, Object> dict = new Hashtable<>(endpoint.getProperties());
            return f.match(dict);
        } catch (Exception e) {
            return false;
        }
    }
}
