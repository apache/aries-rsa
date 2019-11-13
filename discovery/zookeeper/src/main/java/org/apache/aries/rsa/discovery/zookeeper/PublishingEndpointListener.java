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

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.aries.rsa.discovery.zookeeper.client.ClientManager;
import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

/**
 * Listens for local {@link EndpointEvent}s using {@link EndpointEventListener} and old style {@link EndpointListener} 
 * and publishes changes to the {@link ZooKeeperEndpointRepository}
 */
@SuppressWarnings("deprecation")
@Component(service = {}, immediate = true)
public class PublishingEndpointListener implements EndpointEventListener, EndpointListener {

    private ServiceRegistration<?> listenerReg;
    
    @Reference
    private ZookeeperEndpointRepository repository;

    @Activate
    public void start(BundleContext bctx) {
        String uuid = bctx.getProperty(Constants.FRAMEWORK_UUID);
        String[] ifAr = {EndpointEventListener.class.getName(), EndpointListener.class.getName()};
        Dictionary<String, String> props = serviceProperties(uuid);
        listenerReg = bctx.registerService(ifAr, this, props);
    }

    @Deactivate
    public void stop() {
        listenerReg.unregister();
    }

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        repository.endpointChanged(event);
    }
    
    @Override
    public void endpointAdded(EndpointDescription endpoint, String matchedFilter) {
        endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint), matchedFilter);
    }

    @Override
    public void endpointRemoved(EndpointDescription endpoint, String matchedFilter) {
        endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, endpoint), matchedFilter);
    }

    private Dictionary<String, String> serviceProperties(String uuid) {
        String scope = String.format("(&(%s=*)(%s=%s))", Constants.OBJECTCLASS, 
                        RemoteConstants.ENDPOINT_FRAMEWORK_UUID, uuid);
        Dictionary<String, String> props = new Hashtable<>();
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, scope);
        props.put(ClientManager.DISCOVERY_ZOOKEEPER_ID, "true");
        return props;
    }


}
