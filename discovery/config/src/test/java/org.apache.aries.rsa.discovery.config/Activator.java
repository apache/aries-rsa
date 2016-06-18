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

import org.osgi.framework.*;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.util.tracker.ServiceTracker;

import java.util.Hashtable;

@SuppressWarnings("deprecation")
public class Activator implements BundleActivator {
    private ServiceTracker<EndpointListener, EndpointListener> listenerTracker;
    private ConfigDiscovery configDiscovery;
    private ServiceRegistration<ManagedServiceFactory> registration;

    public void start(BundleContext context) {
        configDiscovery = new ConfigDiscovery();
        listenerTracker = new EPListenerTracker(context, configDiscovery);
        listenerTracker.open();
        registration = context.registerService(ManagedServiceFactory.class, configDiscovery, new Hashtable<String, Object>() {{
            put(Constants.SERVICE_PID, "org.apache.aries.rsa.discovery.config");
        }});
    }

    public void stop(BundleContext context) {
        registration.unregister();
        listenerTracker.close();
    }

    private final class EPListenerTracker extends ServiceTracker<EndpointListener, EndpointListener> {
        private final ConfigDiscovery configDiscovery;

        private EPListenerTracker(BundleContext context, ConfigDiscovery configDiscovery) {
            super(context, EndpointListener.class, null);
            this.configDiscovery = configDiscovery;
        }

        @Override
        public EndpointListener addingService(ServiceReference<EndpointListener> reference) {
            EndpointListener service = super.addingService(reference);
            configDiscovery.addListener(reference, service);
            return service;
        }

        @Override
        public void modifiedService(ServiceReference<EndpointListener> reference, EndpointListener service) {
            super.modifiedService(reference, service);
            configDiscovery.removeListener(service);

            // This may cause duplicate registrations of remote services,
            // but that's fine and should be filtered out on another level.
            // See Remote Service Admin spec section 122.6.3
            configDiscovery.addListener(reference, service);
        }

        @Override
        public void removedService(ServiceReference<EndpointListener> reference, EndpointListener service) {
            super.removedService(reference, service);
            configDiscovery.removeListener(service);
        }
    }

}
