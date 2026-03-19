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
package org.apache.aries.rsa.discovery.tcp;

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.osgi.service.remoteserviceadmin.EndpointEvent.*;

/**
 * Manages bookkeeping of all known local {@link EndpointEventListener}s along with
 * their interests (scopes), as well as all known remote endpoints, and notifies
 * the former about the latter.
 */
public class InterestManager {
    // listener to its interest
    private final Map<ServiceReference<EndpointEventListener>, Interest> interests = new ConcurrentHashMap<>();
    // peer framework UUID to endpointId to endpoint
    private final Map<String , Map<String, EndpointDescription>> remoteEndpoints = new ConcurrentHashMap<>();

    public void addListener(ServiceReference<EndpointEventListener> sref,
            EndpointEventListener listener, boolean isNew) {
        // a new listener must be notified immediately of all previously known remote endpoints.
        // an existing listener is not notified, but we do need to update its scopes (interest)
        Interest interest = new Interest(sref, listener);
        interests.put(sref, interest); // if it already exists - replace it with the new interest (scopes)
        if (isNew) {
            // notify new listener of all known remote endpoints
            remoteEndpoints.values().stream()
                .flatMap(endpoints -> endpoints.values().stream())
                .forEach(endpoint -> interest.notifyListener(new EndpointEvent(ADDED, endpoint)));
        }
    }

    public void removeListener(ServiceReference<EndpointEventListener> sref) {
        interests.remove(sref);
    }

    private void notifyAllListeners(int type, EndpointDescription endpoint) {
        EndpointEvent event = new EndpointEvent(type, endpoint);
        interests.values().forEach(interest -> interest.notifyListener(event));
    }

    public void addEndpoint(EndpointDescription endpoint) {
      Map<String, EndpointDescription> endpoints = remoteEndpoints
          .computeIfAbsent(endpoint.getFrameworkUUID(), s -> new ConcurrentHashMap<>());
        boolean exists = endpoints.put(endpoint.getId(), endpoint) != null;
        notifyAllListeners(exists ? MODIFIED : ADDED, endpoint);
    }

    public void removeEndpoint(String peerUuid, String endpointId) {
        Map<String, EndpointDescription> endpoints = remoteEndpoints.get(peerUuid);
        if (endpoints != null) {
            EndpointDescription endpoint = endpoints.remove(endpointId);
            if (endpoint != null) {
                notifyAllListeners(REMOVED, endpoint);
            }
        }
    }

    public void removePeer(String peerUuid) {
        Map<String, EndpointDescription> endpoints = remoteEndpoints.remove(peerUuid);
        if (endpoints != null) {
            endpoints.values().forEach(endpoint -> notifyAllListeners(REMOVED, endpoint));
        }
    }
}
