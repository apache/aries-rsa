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
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

import java.util.*;

/**
 * Monitors adding/updating/removing of configuration instances under the factory pid,
 * as well as adding/removing of EndpointEventListeners, and triggers EndpointEvents
 * accordingly as endpoints are created/updated/removed (via the configuration changes).
 */
class ConfigDiscovery implements ManagedServiceFactory {
    private final Map<String, EndpointDescription> endpoints = new HashMap<>();
    private final Map<EndpointEventListener, Collection<Filter>> listenerToFilters = new HashMap<>();

    @Override
    public String getName() {
        return "Aries RSA Config Discovery";
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) {
        EndpointDescription endpoint = new EndpointDescription(PropertyValidator.validate(properties));
        EndpointDescription old;
        Set<Map.Entry<Filter, EndpointEventListener>> oldMatches, newMatches;
        synchronized (this) {
            old = endpoints.put(pid, endpoint);
            oldMatches = old == null ? null : new HashSet<>(findMatches(old));
            newMatches = new HashSet<>(findMatches(endpoint));
        }
        List<Map.Entry<Filter, EndpointEventListener>> added = new ArrayList<>(newMatches);
        if (old != null) {
            added.removeAll(oldMatches);
            List<Map.Entry<Filter, EndpointEventListener>> endmatch = new ArrayList<>(oldMatches);
            endmatch.removeAll(newMatches);
            List<Map.Entry<Filter, EndpointEventListener>> modified = new ArrayList<>(oldMatches);
            modified.removeAll(endmatch);
            triggerEvents(new EndpointEvent(EndpointEvent.MODIFIED, endpoint), modified);
            triggerEvents(new EndpointEvent(EndpointEvent.MODIFIED_ENDMATCH, old), endmatch);
        }
        triggerEvents(new EndpointEvent(EndpointEvent.ADDED, endpoint), added);
    }

    @Override
    public void deleted(String pid) {
        EndpointDescription endpoint;
        List<Map.Entry<Filter, EndpointEventListener>> matched;
        synchronized (this) {
            endpoint = endpoints.remove(pid);
            if (endpoint == null) {
                return;
            }
            matched = findMatches(endpoint);
        }
        triggerEvents(new EndpointEvent(EndpointEvent.REMOVED, endpoint), matched);
    }

    private static Collection<Filter> createFilters(ServiceReference<EndpointEventListener> ref) {
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

    void addListener(ServiceReference<EndpointEventListener> ref, EndpointEventListener listener) {
        Collection<Filter> filters = createFilters(ref);
        if (!filters.isEmpty()) {
            List<Map.Entry<Filter, EndpointDescription>> matched;
            synchronized (this) {
                listenerToFilters.put(listener, filters);
                matched = findMatches(filters);
            }
            triggerEvents(listener, matched);
        }
    }

    void removeListener(EndpointEventListener listener) {
        synchronized (this) {
            listenerToFilters.remove(listener);
        }
    }

    private List<Map.Entry<Filter, EndpointEventListener>> findMatches(EndpointDescription endpoint) {
        // called with lock, makes a copy of matched filters/listeners so we can later trigger events without locks
        List<Map.Entry<Filter, EndpointEventListener>> matched = new ArrayList<>();
        Dictionary<String, Object> props = new Hashtable<>(endpoint.getProperties());
        for (Map.Entry<EndpointEventListener, Collection<Filter>> entry : listenerToFilters.entrySet()) {
            EndpointEventListener listener = entry.getKey();
            for (Filter filter : entry.getValue()) {
                if (filter.match(props)) { // don't use matches() which is case-sensitive
                    matched.add(Map.entry(filter, listener));
                }
            }
        }
        return matched;
    }

    private List<Map.Entry<Filter, EndpointDescription>> findMatches(Collection<Filter> filters) {
        // called with lock, makes a copy of matched filters/endpoints so we can later trigger events without locks
        List<Map.Entry<Filter, EndpointDescription>> matched = new ArrayList<>();
        for (EndpointDescription endpoint : endpoints.values()) {
            Dictionary<String, Object> props = new Hashtable<>(endpoint.getProperties());
            for (Filter filter : filters) {
                if (filter.match(props)) { // don't use matches() which is case-sensitive
                    matched.add(Map.entry(filter, endpoint));
                }
            }
        }
        return matched;
    }

    private void triggerEvents(EndpointEvent event, List<Map.Entry<Filter, EndpointEventListener>> matched) {
        // trigger events without holding a lock
        for (Map.Entry<Filter, EndpointEventListener> entry : matched) {
            entry.getValue().endpointChanged(event, entry.getKey().toString());
        }
    }

    private void triggerEvents(EndpointEventListener listener, List<Map.Entry<Filter, EndpointDescription>> matched) {
        // trigger events without holding a lock
        for (Map.Entry<Filter, EndpointDescription> entry : matched) {
            EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, entry.getValue());
            listener.endpointChanged(event, entry.getKey().toString());
        }
    }
}
