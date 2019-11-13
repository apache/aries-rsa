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
package org.apache.aries.rsa.topologymanager.exporter;

import java.util.Collection;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks EndpointListeners and allows to notify them of endpoints.
 */
@SuppressWarnings("deprecation")
public class EndpointListenerNotifier {
    private static final Logger LOG = LoggerFactory.getLogger(EndpointListenerNotifier.class);
    private Map<EndpointEventListener, Set<Filter>> listeners;

    public EndpointListenerNotifier() {
        this.listeners = new ConcurrentHashMap<>();
    }
   
    public static Set<Filter> filtersFromEL(ServiceReference<EndpointListener> sref) {
        List<String> scopes = StringPlus.normalize(sref.getProperty(EndpointListener.ENDPOINT_LISTENER_SCOPE));
        return getFilterSet(scopes);
    }
    
    public static Set<Filter> filtersFromEEL(ServiceReference<EndpointEventListener> sref) {
        List<String> scopes = StringPlus.normalize(sref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE));
        return getFilterSet(scopes);
    }

    private static Set<Filter> getFilterSet(List<String> scopes) {
        Set<Filter> filters = new HashSet<>();
        for (String scope : scopes) {
            try {
                filters.add(FrameworkUtil.createFilter(scope));
            } catch (InvalidSyntaxException e) {
                LOG.error("invalid endpoint listener scope: {}", scope, e);
            }
        }
        return filters;
    }
    
    public void add(EndpointEventListener ep, Set<Filter> filters, Collection<EndpointDescription> endpoints) {
        LOG.debug("EndpointListener added");
        listeners.put(ep, filters);
        for (EndpointDescription endpoint : endpoints) {
            EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
            notifyListener(event, ep, filters);
        }
    }
    
    public void remove(EndpointEventListener ep) {
        LOG.debug("EndpointListener removed");
        listeners.remove(ep);
    }
    
    public void sendEvent(EndpointEvent event) {
        for (EndpointEventListener listener : listeners.keySet()) {
            Set<Filter> filters = listeners.get(listener);
            notifyListener(event, listener, filters);
        }
    }

    /**
     * Notifies an endpoint listener about endpoints being added or removed.
     *
     * @param type specifies whether endpoints were added (true) or removed (false)
     * @param endpointListenerRef the ServiceReference of an EndpointListener to notify
     * @param endpoints the endpoints the listener should be notified about
     */
    private void notifyListener(EndpointEvent event, EndpointEventListener listener, Set<Filter> filters) {
        Set<Filter> matchingFilters = getMatchingFilters(filters, event.getEndpoint());
        for (Filter filter : matchingFilters) {
            listener.endpointChanged(event, filter.toString());
        }
    }
    
    private static Set<Filter> getMatchingFilters(Set<Filter> filters, EndpointDescription endpoint) {
        Set<Filter> matchingFilters = new HashSet<>();
        if (endpoint == null) {
            return matchingFilters;
        }
        Dictionary<String, Object> dict = new Hashtable<>(endpoint.getProperties());
        for (Filter filter : filters) {
            if (filter.match(dict)) {
                LOG.debug("Filter {} matches endpoint {}", filter, dict);
                matchingFilters.add(filter);
            } else {
                LOG.trace("Filter {} does not match endpoint {}", filter, dict);
            }
        }
        return matchingFilters;
    }
}
