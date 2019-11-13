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
package org.apache.aries.rsa.discovery.zookeeper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.aries.rsa.discovery.zookeeper.client.ClientManager;
import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointListener;
import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the {@link EndpointEventListener}s and the scopes they are interested in.
 * Establishes a listener with the {@link ZookeeperEndpointRepository} to be called back on all changes in the repository.
 * Events from repository are then forwarded to all interested {@link EndpointEventListener}s.
 */
@SuppressWarnings("deprecation")
@Component(immediate = true)
public class InterestManager {
    private static final Logger LOG = LoggerFactory.getLogger(InterestManager.class);

    private Set<Interest> interests = ConcurrentHashMap.newKeySet();
    
    private ZookeeperEndpointListener listener;
    
    public InterestManager() {
    }
    
    // Using ARepository name to make sure it is injected first
    @Reference
    public void bindARepository(ZookeeperEndpointRepository repository) {
        this.listener = repository.createListener(this::onEndpointChanged);
    }
    
    @Deactivate
    public void deactivate() {
        this.listener.close();
        interests.clear();
    }

    private void onEndpointChanged(EndpointEvent event, String filter) {
        interests.forEach(interest -> interest.notifyListener(event));
    }
    
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindEndpointEventListener(ServiceReference<EndpointEventListener> sref, EndpointEventListener epListener) {
        addInterest(sref, epListener);
    }
    
    public void updatedEndpointEventListener(ServiceReference<EndpointEventListener> sref, EndpointEventListener epListener) {
        addInterest(sref, epListener);
    }
    
    public void unbindEndpointEventListener(ServiceReference<EndpointEventListener> sref) {
        interests.remove(new Interest(sref));
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindEndpointListener(ServiceReference<EndpointListener> sref, EndpointListener epListener) {
        addInterest(sref, epListener);
    }
    
    public void updatedEndpointListener(ServiceReference<EndpointListener> sref, EndpointListener epListener) {
        addInterest(sref, epListener);
    }
    
    public void unbindEndpointListener(ServiceReference<EndpointListener> sref) {
        interests.remove(new Interest(sref));
    }

    private void addInterest(ServiceReference<?> sref, Object epListener) {
        if (isOurOwnEndpointEventListener(sref)) {
            LOG.debug("Skipping our own EndpointEventListener");
            return;
        }
        Interest interest = new Interest(sref, epListener);
        update(interest);
        if (listener != null) {
            listener.sendExistingEndpoints(interest);
        }
    }

    private void update(Interest interest) {
        boolean present = interests.remove(interest);
        LOG.debug("{} Interest: {}", present ? "Adding" : "Updating", interest);
        interests.add(interest);
    }

    private static boolean isOurOwnEndpointEventListener(ServiceReference<?> endpointEventListener) {
        return Boolean.parseBoolean(String.valueOf(
                endpointEventListener.getProperty(ClientManager.DISCOVERY_ZOOKEEPER_ID)));
    }
    
    Set<Interest> getInterests() {
        return interests;
    }

}
