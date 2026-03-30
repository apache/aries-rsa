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
package org.apache.aries.rsa.discovery.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class LocalDiscovery implements BundleListener {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDiscovery.class);

    final Map<Bundle, Collection<EndpointDescription>> endpoints = new ConcurrentHashMap<>();
    final Map<EndpointEventListener, Collection<Filter>> listenerToFilters = new HashMap<>();

    EndpointDescriptionBundleParser parser;

    public LocalDiscovery() {
        this.parser = new EndpointDescriptionBundleParser();
    }

    private static List<Filter> createFilters(ServiceReference<EndpointEventListener> ref) {
        List<String> values = StringPlus.normalize(ref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE));
        List<Filter> filters = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                filters.add(FrameworkUtil.createFilter(value));
            } catch (InvalidSyntaxException ise) {
                // invalid filter syntax means it can't match anything anyway, so we just ignore it
                LOG.trace("ignoring invalid filter '{}'", value);
            }
        }
        return filters;
    }

    @Activate
    public void activate(BundleContext context) {
        context.addBundleListener(this);
        processExistingBundles(context.getBundles());
    }

    @Deactivate
    public void deactivate(BundleContext context) {
        context.removeBundleListener(this);
    }

    protected void processExistingBundles(Bundle[] bundles) {
        for (Bundle bundle : bundles) {
            if (bundle.getState() == Bundle.ACTIVE) {
                addEndpoints(bundle);
            }
        }
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindListener(ServiceReference<EndpointEventListener> sref, EndpointEventListener listener) {
        List<Filter> filters = createFilters(sref);
        if (filters.isEmpty()) {
            return;
        }

        synchronized (listenerToFilters) {
            listenerToFilters.put(listener, filters);
        }

        publishAllToListener(filters, listener);
    }

    /**
     * If the tracker was removed or the scope was changed this doesn't require
     * additional callbacks on the tracker. It's the responsibility of the tracker
     * itself to clean up any orphans. See Remote Service Admin spec 122.6.3
     *
     * @param listener the listener being removed
     */
    void unbindListener(EndpointEventListener listener) {
        synchronized (listenerToFilters) {
            listenerToFilters.remove(listener);
        }
    }

    void updatedListener(ServiceReference<EndpointEventListener> sref, EndpointEventListener listener) {
        // if service properties have been updated, the filter (scope)
        // might have changed so we remove and re-add the listener
        // TODO fix this so that we don't:
        // 1. remove and add when there is no change
        // 2. remove and add instead of modifying
        // 3. remove instead of modified end match
        synchronized (listenerToFilters) {
            unbindListener(listener);
            bindListener(sref, listener);
        }
    }

    private Map<Filter, Collection<EndpointEventListener>> getMatchingListeners(EndpointDescription endpoint) {
        // return a copy of matched filters/listeners so that caller doesn't need to hold locks while triggering events
        Hashtable<String, Object> props = new Hashtable<>(endpoint.getProperties());
        Map<Filter, Collection<EndpointEventListener>> matched = new HashMap<>();
        synchronized (listenerToFilters) {
            for (Entry<EndpointEventListener, Collection<Filter>> entry : listenerToFilters.entrySet()) {
                for (Filter filter : entry.getValue()) {
                    if (filter.match(props)) { // don't use matches() which is case-sensitive
                        matched.computeIfAbsent(filter, f -> new ArrayList<>()).add(entry.getKey());
                    }
                }
            }
        }
        return matched;
    }

    // BundleListener method
    @Override
    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                addEndpoints(event.getBundle());
                break;
            case BundleEvent.STOPPED:
                removeEndpoints(event.getBundle());
                break;
            default:
        }
    }

    private void addEndpoints(Bundle bundle) {
        Collection<EndpointDescription> endpoints = parser.getAllEndpointDescriptions(bundle);
        if (!endpoints.isEmpty()) {
            this.endpoints.put(bundle, endpoints);
            endpoints.forEach(endpoint -> publishToAllListeners(new EndpointEvent(EndpointEvent.ADDED, endpoint)));
        }
    }

    private void removeEndpoints(Bundle bundle) {
        Collection<EndpointDescription> endpoints = this.endpoints.remove(bundle);
        if (endpoints != null) {
            endpoints.forEach(endpoint -> publishToAllListeners(new EndpointEvent(EndpointEvent.REMOVED, endpoint)));
        }
    }

    private void publishToAllListeners(EndpointEvent event) {
        Map<Filter, Collection<EndpointEventListener>> matched = getMatchingListeners(event.getEndpoint());
        for (Map.Entry<Filter, Collection<EndpointEventListener>> entry : matched.entrySet()) {
            for (EndpointEventListener listener : entry.getValue()) {
                listener.endpointChanged(event, entry.getKey().toString());
            }
        }
    }

    private void publishAllToListener(Collection<Filter> filters, EndpointEventListener listener) {
        endpoints.values().stream().flatMap(Collection::stream)
            .forEach(endpoint -> {
                EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
                Hashtable<String, Object> props = new Hashtable<>(event.getEndpoint().getProperties());
                filters.forEach(filter -> {
                    if (filter.match(props)) {
                        listener.endpointChanged(event, filter.toString());
                    }
                });
            });
    }
}
