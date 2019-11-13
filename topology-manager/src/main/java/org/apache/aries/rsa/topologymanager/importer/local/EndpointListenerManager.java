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
package org.apache.aries.rsa.topologymanager.importer.local;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the endpoint listener for the import of external services.
 * The endpoint listener scope reflects the combined filters of all services 
 * that are asked for (by listeners and service lookups) in the current system. 
 * 
 * Discovery will then send callbacks when external endpoints are added / removed that match
 * the interest in the local system.
 */
@SuppressWarnings("deprecation")
public class EndpointListenerManager implements ServiceInterestListener{

    private final class EndpointListenerAdapter implements EndpointListener, EndpointEventListener {
        @Override
        public void endpointRemoved(EndpointDescription endpoint, String matchedFilter) {
            EndpointEvent event = new EndpointEvent(EndpointEvent.REMOVED, endpoint);
            endpointListener.endpointChanged(event, matchedFilter);
        }

        @Override
        public void endpointAdded(EndpointDescription endpoint, String matchedFilter) {
            EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
            endpointListener.endpointChanged(event, matchedFilter);
        }

        @Override
        public void endpointChanged(EndpointEvent event, String filter) {
            endpointListener.endpointChanged(event, filter);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(EndpointListenerManager.class);

    private final BundleContext bctx;
    private volatile ServiceRegistration<?> serviceRegistration;
    
    private final List<String> filters = new ArrayList<>();
    private final EndpointEventListener endpointListener;
    private final ListenerHookImpl listenerHook;
    private RSFindHook findHook;
    
    /**
     * Count service interest by filter. This allows to modify the scope of the EndpointListener as seldom as possible
     */
    private final ReferenceCounter<String> importInterestsCounter = new ReferenceCounter<>();

    public EndpointListenerManager(BundleContext bc, EndpointEventListener endpointListener) {
        this.bctx = bc;
        this.endpointListener = endpointListener;
        this.listenerHook = new ListenerHookImpl(bc, this);
        findHook = new RSFindHook(bc, this);
    }

    public void start() {
        EndpointListener endpointListenerAdapter = new EndpointListenerAdapter();
        String[] ifAr = new String[] {EndpointListener.class.getName(), EndpointEventListener.class.getName()};
        serviceRegistration = bctx.registerService(ifAr, endpointListenerAdapter,
                                                   getEELProperties());

        bctx.registerService(ListenerHook.class, listenerHook, null);
        bctx.registerService(FindHook.class, findHook, null);
    }

    public void stop() {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    protected void extendScope(String filter) {
        if (filter == null) {
            return;
        }
        LOG.debug("EndpointListener: extending scope by {}", filter);
        synchronized (filters) {
            filters.add(filter);
        }
        updateRegistration();
    }

    protected void reduceScope(String filter) {
        if (filter == null) {
            return;
        }
        LOG.debug("EndpointListener: reducing scope by {}", filter);
        synchronized (filters) {
            filters.remove(filter);
        }
        updateRegistration();
    }

    private Dictionary<String, Object> getEELProperties() {
        Dictionary<String, Object> p = new Hashtable<>();
        p.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, copyFilters());
        return p;
    }
    
    public List<String> copyFilters() {
        synchronized (filters) {
            return new ArrayList<>(filters);
        }
    }

    private void updateRegistration() {
        if (serviceRegistration != null) {
            serviceRegistration.setProperties(getEELProperties());
        }
    }

    @Override
    public void addServiceInterest(String filter) {
        if (importInterestsCounter.add(filter) == 1) {
            extendScope(filter);
        }
    }

    @Override
    public void removeServiceInterest(String filter) {
        if (importInterestsCounter.remove(filter) == 0) {
            LOG.debug("last reference to import interest is gone -> removing interest filter: {}", filter);
            reduceScope(filter);
        }
    }
}
