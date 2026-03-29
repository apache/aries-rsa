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
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
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

@Component(immediate = true)
public class LocalDiscovery implements BundleListener {

    // this is effectively a set which allows for multiple service descriptions with the
    // same interface name but different properties and takes care of itself with respect to concurrency
    final Map<EndpointDescription, Bundle> endpointDescriptions = new ConcurrentHashMap<>();
    final Map<EndpointEventListener, Collection<String>> listenerToFilters = new HashMap<>();
    final Map<String, Collection<EndpointEventListener>> filterToListeners = new HashMap<>();

    EndpointDescriptionBundleParser bundleParser;

    public LocalDiscovery() {
        this.bundleParser = new EndpointDescriptionBundleParser();
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
        for (Bundle b : bundles) {
            if (b.getState() == Bundle.ACTIVE) {
                addDeclaredRemoteServices(b);
            }
        }
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindListener(ServiceReference<EndpointEventListener> endpointListenerRef, EndpointEventListener endpointListener) {
        List<String> filters = StringPlus.normalize(endpointListenerRef.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE));
        if (filters.isEmpty()) {
            return;
        }

        synchronized (listenerToFilters) {
            listenerToFilters.put(endpointListener, filters);
            for (String filter : filters) {
                filterToListeners.computeIfAbsent(filter, k -> new ArrayList<>()).add(endpointListener);
            }
        }

        triggerCallbacks(filters, endpointListener);
    }

    /**
     * If the tracker was removed or the scope was changed this doesn't require
     * additional callbacks on the tracker. It's the responsibility of the tracker
     * itself to clean up any orphans. See Remote Service Admin spec 122.6.3
     * @param endpointListener
     */
    void unbindListener(EndpointEventListener endpointListener) {
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

    void updatedListener(ServiceReference<EndpointEventListener> endpointListenerRef, EndpointEventListener endpointListener) {
        // if service properties have been updated, the filter (scope)
        // might have changed so we remove and re-add the listener
        // TODO fix this so that we don't:
        // 1. remove and add when there is no change
        // 2. remove and add instead of modifying
        // 3. remove instead of modified end match
        synchronized (listenerToFilters) {
            unbindListener(endpointListener);
            bindListener(endpointListenerRef, endpointListener);
        }
    }

    private Map<String, Collection<EndpointEventListener>> getMatchingListeners(EndpointDescription endpoint) {
        // return a copy of matched filters/listeners so that caller doesn't need to hold locks while triggering events
        Map<String, Collection<EndpointEventListener>> matched = new HashMap<>();
        synchronized (listenerToFilters) {
            for (Entry<String, Collection<EndpointEventListener>> entry : filterToListeners.entrySet()) {
                String filter = entry.getKey();
                if (LocalDiscovery.matchFilter(filter, endpoint)) {
                    matched.put(filter, new ArrayList<>(entry.getValue()));
                }
            }
        }
        return matched;
    }

    // BundleListener method
    @Override
    public void bundleChanged(BundleEvent be) {
        switch (be.getType()) {
        case BundleEvent.STARTED:
            addDeclaredRemoteServices(be.getBundle());
            break;
        case BundleEvent.STOPPED:
            removeDeclaredRemoteServices(be.getBundle());
            break;
        default:
        }
    }

    private void addDeclaredRemoteServices(Bundle bundle) {
        List<EndpointDescription> endpoints = bundleParser.getAllEndpointDescriptions(bundle);
        for (EndpointDescription endpoint : endpoints) {
            endpointDescriptions.put(endpoint, bundle);
            EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
            triggerCallbacks(event);
        }
    }

    private void removeDeclaredRemoteServices(Bundle bundle) {
        for (Iterator<Entry<EndpointDescription, Bundle>> i = endpointDescriptions.entrySet().iterator();
            i.hasNext();) {
            Entry<EndpointDescription, Bundle> entry = i.next();
            if (bundle.equals(entry.getValue())) {
                EndpointEvent event = new EndpointEvent(EndpointEvent.REMOVED, entry.getKey());
                triggerCallbacks(event);
                i.remove();
            }
        }
    }

    private void triggerCallbacks(EndpointEvent event) {
        Map<String, Collection<EndpointEventListener>> matched = getMatchingListeners(event.getEndpoint());
        for (Map.Entry<String, Collection<EndpointEventListener>> entry : matched.entrySet()) {
            for (EndpointEventListener listener : entry.getValue()) {
                triggerCallbacks(listener, entry.getKey(), event);
            }
        }
    }

    private void triggerCallbacks(Collection<String> filters, EndpointEventListener endpointListener) {
        for (String filter : filters) {
            for (EndpointDescription endpoint : endpointDescriptions.keySet()) {
                EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
                triggerCallbacks(endpointListener, filter, event);
            }
        }
    }

    private void triggerCallbacks(EndpointEventListener endpointListener, String filter, EndpointEvent event) {
        if (LocalDiscovery.matchFilter(filter, event.getEndpoint())) {
            endpointListener.endpointChanged(event, filter);
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
