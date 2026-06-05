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

import java.util.Collection;

import org.apache.aries.rsa.spi.discovery.InterestManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class LocalDiscovery implements BundleListener {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDiscovery.class);

    final InterestManager interestManager;

    EndpointDescriptionBundleParser parser;

    public LocalDiscovery() {
        this.parser = new EndpointDescriptionBundleParser();
        this.interestManager = new InterestManager();
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
        interestManager.addListener(sref, listener);
    }

    void unbindListener(ServiceReference<EndpointEventListener> sref) {
        interestManager.removeListener(sref);
    }

    void updatedListener(ServiceReference<EndpointEventListener> sref, EndpointEventListener listener) {
        interestManager.updateListener(sref, listener);
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
        for (EndpointDescription endpoint : endpoints) {
            interestManager.addEndpoint(String.valueOf(bundle.getBundleId()), endpoint);
        }
    }

    private void removeEndpoints(Bundle bundle) {
        interestManager.removeSource(String.valueOf(bundle.getBundleId()));
    }
}
