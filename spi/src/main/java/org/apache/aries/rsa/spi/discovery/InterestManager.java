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
package org.apache.aries.rsa.spi.discovery;

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.aries.rsa.util.CollectionUtils.getChangedKeys;

/**
 * Manages the bookkeeping of all known local {@link EndpointEventListener}s
 * along with their interests (scopes) so that they can be notified of
 * the endpoints they are interested in, and all known remote endpoints
 * so that new listeners can be notified of all of them.
 */
public class InterestManager {
    private static final Logger LOG = LoggerFactory.getLogger(InterestManager.class);
    // listener to its interest
    protected final Map<ServiceReference<EndpointEventListener>, Interest> interests = new ConcurrentHashMap<>();
    // source to endpointId to endpoint
    protected final Map<String , Map<String, EndpointDescription>> remoteEndpoints = new ConcurrentHashMap<>();

    public void addListener(ServiceReference<EndpointEventListener> sref,
            EndpointEventListener listener) {
        // a new listener must be notified immediately of all previously known remote endpoints.
        // an existing listener is not notified, but we do need to update its scopes (interest)
        Interest interest = new Interest(sref, listener);
        boolean exists = interests.put(sref, interest) != null;
        LOG.debug("{} interest {}, notifying listener of endpoints {}",
            exists ? "Updated" : "Added", interest, remoteEndpoints.values());
        // notify new or updated listener of all known remote endpoints
        // (according to the spec, the listener is idempotent so we don't worry about ADDED duplicates)
        remoteEndpoints.values().stream()
            .flatMap(endpoints -> endpoints.values().stream())
                .forEach(endpoint -> interest.notifyListener(null, endpoint));
    }

    public void updateListener(ServiceReference<EndpointEventListener> sref,
            EndpointEventListener listener) {
        addListener(sref, listener);
    }

    public void removeListener(ServiceReference<EndpointEventListener> sref) {
        interests.remove(sref);
    }

    private void notifyAllListeners(EndpointDescription old, EndpointDescription endpoint) {
        // filter out duplicate endpoints (which may be received according to the spec)
        if (old != null && endpoint != null && getChangedKeys(old.getProperties(), endpoint.getProperties()).isEmpty()) {
            LOG.trace("ignoring unmodified endpoint: {}", endpoint);
            return;
        }
        interests.values().forEach(interest -> interest.notifyListener(old, endpoint));
    }

    public void addEndpoint(String source, EndpointDescription endpoint) {
        Map<String, EndpointDescription> endpoints =
            remoteEndpoints.computeIfAbsent(source, s -> new ConcurrentHashMap<>());
        EndpointDescription old = endpoints.put(endpoint.getId(), endpoint);
        notifyAllListeners(old, endpoint);
    }

    public void removeEndpoint(String source, String endpointId) {
        Map<String, EndpointDescription> endpoints = remoteEndpoints.get(source);
        if (endpoints != null) {
            EndpointDescription endpoint = endpoints.remove(endpointId);
            if (endpoint != null) {
                notifyAllListeners(endpoint, null);
            }
        }
    }

    public void removeSource(String source) {
        Map<String, EndpointDescription> endpoints = remoteEndpoints.remove(source);
        if (endpoints != null) {
            endpoints.values().forEach(endpoint -> notifyAllListeners(endpoint, null));
        }
    }
}
