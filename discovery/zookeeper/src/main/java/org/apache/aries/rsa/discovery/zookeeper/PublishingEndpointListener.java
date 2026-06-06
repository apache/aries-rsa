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
import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.apache.aries.rsa.spi.discovery.LocalEndpointManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

/**
 * Listens for local {@link EndpointEvent}s using {@link EndpointEventListener}
 * and publishes changes to the {@link ZookeeperEndpointRepository}
 */
@SuppressWarnings("deprecation")
@Component(service = {}, immediate = true)
public class PublishingEndpointListener {

    protected LocalEndpointManager localEndpointManager;

    @Reference
    private ZookeeperEndpointRepository repository;

    @Activate
    public void start(BundleContext context) {
        localEndpointManager = new LocalEndpointManager();
        localEndpointManager.setListener((event, filter) -> repository.endpointChanged(event));
        localEndpointManager.start(context, ClientManager.DISCOVERY_ZOOKEEPER_ID);
    }

    @Deactivate
    public void stop() {
        localEndpointManager.stop();
    }
}
