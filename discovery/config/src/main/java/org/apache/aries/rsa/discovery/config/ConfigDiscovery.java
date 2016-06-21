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
import org.osgi.service.remoteserviceadmin.EndpointListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class ConfigDiscovery implements ManagedServiceFactory {
    private final Map<EndpointDescription, String> endpointDescriptions = new ConcurrentHashMap<>();
    private final Map<EndpointListener, Collection<String>> listenerToFilters = new HashMap<>();
    private final Map<String, Collection<EndpointListener>> filterToListeners = new HashMap<>();

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

    void addListener(ServiceReference<EndpointListener> endpointListenerRef, EndpointListener endpointListener) {
        List<String> filters = StringPlus.normalize(endpointListenerRef.getProperty(EndpointListener.ENDPOINT_LISTENER_SCOPE));
        if (filters.isEmpty()) {
            return;
        }

        synchronized (listenerToFilters) {
            listenerToFilters.put(endpointListener, filters);
            for (String filter : filters) {
                Collection<EndpointListener> listeners = filterToListeners.get(filter);
                if (listeners == null) {
                    listeners = new ArrayList<>();
                    filterToListeners.put(filter, listeners);
                }
                listeners.add(endpointListener);
            }
        }

        triggerCallbacks(filters, endpointListener);
    }

    void removeListener(EndpointListener endpointListener) {
        synchronized (listenerToFilters) {
            Collection<String> filters = listenerToFilters.remove(endpointListener);
            if (filters == null) {
                return;
            }

            for (String filter : filters) {
                Collection<EndpointListener> listeners = filterToListeners.get(filter);
                if (listeners != null) {
                    listeners.remove(endpointListener);
                    if (listeners.isEmpty()) {
                        filterToListeners.remove(filter);
                    }
                }
            }
        }
    }

    private Map<String, Collection<EndpointListener>> getMatchingListeners(EndpointDescription endpoint) {
        // return a copy of matched filters/listeners so that caller doesn't need to hold locks while triggering events
        Map<String, Collection<EndpointListener>> matched = new HashMap<>();
        synchronized (listenerToFilters) {
            for (Map.Entry<String, Collection<EndpointListener>> entry : filterToListeners.entrySet()) {
                String filter = entry.getKey();
                if (matchFilter(filter, endpoint)) {
                    matched.put(filter, new ArrayList<>(entry.getValue()));
                }
            }
        }
        return matched;
    }

    private void addDeclaredRemoteService(String pid, Dictionary config) {
        EndpointDescription endpoint = new EndpointDescription(PropertyValidator.validate(config));
        endpointDescriptions.put(endpoint, pid);
        addedEndpointDescription(endpoint);
    }

    private void removeServiceDeclaredInConfig(String pid) {
        for (Iterator<Map.Entry<EndpointDescription, String>> i = endpointDescriptions.entrySet().iterator();
             i.hasNext(); ) {
            Map.Entry<EndpointDescription, String> entry = i.next();
            if (pid.equals(entry.getValue())) {
                removedEndpointDescription(entry.getKey());
                i.remove();
            }
        }
    }

    private void addedEndpointDescription(EndpointDescription endpoint) {
        triggerCallbacks(endpoint, true);
    }

    private void removedEndpointDescription(EndpointDescription endpoint) {
        triggerCallbacks(endpoint, false);
    }

    private void triggerCallbacks(EndpointDescription endpoint, boolean added) {
        for (Map.Entry<String, Collection<EndpointListener>> entry : getMatchingListeners(endpoint).entrySet()) {
            String filter = entry.getKey();
            for (EndpointListener listener : entry.getValue()) {
                triggerCallbacks(listener, filter, endpoint, added);
            }
        }
    }

    private void triggerCallbacks(EndpointListener endpointListener, String filter,
                                  EndpointDescription endpoint, boolean added) {
        if (!matchFilter(filter, endpoint)) {
            return;
        }

        if (added) {
            endpointListener.endpointAdded(endpoint, filter);
        } else {
            endpointListener.endpointRemoved(endpoint, filter);
        }
    }

    private void triggerCallbacks(Collection<String> filters, EndpointListener endpointListener) {
        for (String filter : filters) {
            for (EndpointDescription endpoint : endpointDescriptions.keySet()) {
                triggerCallbacks(endpointListener, filter, endpoint, true);
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
