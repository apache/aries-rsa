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
package org.apache.aries.rsa.core;

import static org.osgi.service.remoteserviceadmin.RemoteConstants.REMOTE_CONFIGS_SUPPORTED;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.REMOTE_INTENTS_SUPPORTED;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.aries.rsa.core.event.EventProducer;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks registered DistributionProviders, and for each one registers
 * a {@link RemoteServiceAdminFactory} with an associated {@link RemoteServiceAdminCore}
 * so that each TopologyManager will be given its own {@link RemoteServiceAdminInstance}
 * to manage its exports/imports (backed by the shared core).
 * <p>
 * When the DistributionProvider is unregistered, we unregister its factory,
 * which causes the framework to invoke ungetService for all of its instances,
 * so they all shut down cleanly, close their exports/imports, etc.
 */
public class DistributionProviderTracker extends ServiceTracker<DistributionProvider, ServiceRegistration<RemoteServiceAdminFactory>> {
    private static final Logger LOG = LoggerFactory.getLogger(DistributionProviderTracker.class);

    public DistributionProviderTracker(BundleContext context) {
        super(context, DistributionProvider.class, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ServiceRegistration<RemoteServiceAdminFactory> addingService(ServiceReference<DistributionProvider> reference) {
        DistributionProvider provider = context.getService(reference);
        if (provider == null) {
            // Can happen if the service is created by a service factory and an exception occurs
            return null;
        }
        if (provider.getSupportedTypes() == null || provider.getSupportedTypes().length == 0) {
            LOG.warn("Invalid DistributionProvider {}: no supported config types", provider);
            return null;
        }
        LOG.debug("Initializing RemoteServiceAdmin for DistributionProvider {} ({})",
            provider, Arrays.asList(provider.getSupportedTypes()));
        BundleContext apiContext = getAPIContext();
        EventProducer eventProducer = new EventProducer(context);
        // we create one RSA core per tracked provider, accessed by its corresponding ServiceFactory
        RemoteServiceAdminCore rsaCore = new RemoteServiceAdminCore(context, apiContext, eventProducer, provider);
        RemoteServiceAdminFactory rsaf = new RemoteServiceAdminFactory(rsaCore);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(REMOTE_INTENTS_SUPPORTED, getPropertyNullSafe(reference, REMOTE_INTENTS_SUPPORTED));
        props.put(REMOTE_CONFIGS_SUPPORTED, getPropertyNullSafe(reference, REMOTE_CONFIGS_SUPPORTED));
        LOG.info("Registering RemoteServiceAdmin factory for provider {}", provider.getClass().getName());
        return (ServiceRegistration<RemoteServiceAdminFactory>)
            context.registerService(RemoteServiceAdmin.class.getName(), rsaf, props);
    }

    private Object getPropertyNullSafe(ServiceReference<DistributionProvider> reference, String key) {
        Object value = reference.getProperty(key);
        return value == null ? "" : value;
    }

    protected BundleContext getAPIContext() {
        Bundle apiBundle = FrameworkUtil.getBundle(DistributionProvider.class);
        try {
            apiBundle.start();
        } catch (BundleException e) {
            LOG.error(e.getMessage(), e);
        }
        return apiBundle.getBundleContext();
    }

    @Override
    public void removedService(ServiceReference<DistributionProvider> reference,
           ServiceRegistration<RemoteServiceAdminFactory> factoryRegistration) {
        LOG.debug("Unregistering RemoteServiceAdmin factory for removed DistributionProvider");
        // the provider is gone - unregister its corresponding factory,
        // which will also cause all of its RSA instances (and their imports/exports) to be closed
        factoryRegistration.unregister();
        super.removedService(reference, factoryRegistration);
    }
}
