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

import org.apache.aries.rsa.discovery.zookeeper.client.ClientManager;
import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointListener;
import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the {@link EndpointEventListener}s and the scopes they are interested in.
 * Establishes a listener with the {@link ZookeeperEndpointRepository} to be called back on all changes in the repository.
 * Events from repository are then forwarded to all interested {@link EndpointEventListener}s.
 */
@Component(immediate = true)
public class InterestManager extends org.apache.aries.rsa.spi.discovery.InterestManager {
    private static final Logger LOG = LoggerFactory.getLogger(InterestManager.class);

    private ZookeeperEndpointListener zkListener;

    // Using ARepository name to make sure it is injected first
    @Reference
    public void bindARepository(ZookeeperEndpointRepository repository) {
        zkListener = repository.createListener(this);
    }

    @Activate
    public void activate(BundleContext context) {
        start(context, ClientManager.DISCOVERY_ZOOKEEPER_ID);
    }

    @Deactivate
    public void deactivate() {
        stop();
        zkListener.close();
    }

    int size() {
        return interests.size();
    }
}
