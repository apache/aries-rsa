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
package org.apache.aries.rsa.topologymanager;

import org.apache.aries.rsa.topologymanager.exporter.EndpointListenerAdapter;
import org.apache.aries.rsa.topologymanager.exporter.EndpointListenerNotifier;
import org.apache.aries.rsa.topologymanager.exporter.TopologyManagerExport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings({ "deprecation", "rawtypes", "unchecked" })
final class EndpointEventListenerTracker extends ServiceTracker {
    private TopologyManagerExport tmExport;

    EndpointEventListenerTracker(BundleContext context, TopologyManagerExport tmExport) {
        super(context, getFilter(), null);
        this.tmExport = tmExport;
    }
    
    private static Filter getFilter() {
        String filterSt = String.format("(|(objectClass=%s)(objectClass=%s))", EndpointEventListener.class.getName(), 
                EndpointListener.class.getName());
        try {
            return FrameworkUtil.createFilter(filterSt);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public Object addingService(ServiceReference reference) {
        Object listener = super.addingService(reference);
        EndpointEventListener actualListener = getListener(listener);
        this.tmExport.addEPListener(actualListener, EndpointListenerNotifier.filtersFromEEL(reference));
        return actualListener;
    }

    private EndpointEventListener getListener(Object listener) {
        return (listener instanceof EndpointEventListener) 
                ? (EndpointEventListener) listener
                : new EndpointListenerAdapter((EndpointListener) listener);
    }

    @Override
    public void modifiedService(ServiceReference reference, Object listener) {
        EndpointEventListener actualListener = getListener(listener);
        this.tmExport.addEPListener(actualListener, EndpointListenerNotifier.filtersFromEEL(reference));
        super.modifiedService(reference, actualListener);
    }

    @Override
    public void removedService(ServiceReference reference, Object listener) {
        this.tmExport.removeEPListener((EndpointEventListener) listener);
        super.removedService(reference, listener);
    }
    
}
