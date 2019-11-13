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

import static org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE;

import java.util.List;
import java.util.Optional;

import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class Interest {
    private static final Logger LOG = LoggerFactory.getLogger(Interest.class);
    
    private final ServiceReference<?> sref;
    private final List<String> scopes;
    private final Object epListener;
    
    public Interest(ServiceReference<?> sref) {
        this(sref, null);
    }
    
    public Interest(ServiceReference<?> sref, Object epListener) {
        this.sref = sref;
        this.scopes = StringPlus.normalize(sref.getProperty(ENDPOINT_LISTENER_SCOPE));
        this.epListener = epListener;
    }
    
    public List<String> getScopes() {
        return scopes;
    }

    public Object getEpListener() {
        return epListener;
    }
    
    public void notifyListener(EndpointEvent event) {
        EndpointDescription endpoint = event.getEndpoint();
        Optional<String> currentScope = getFirstMatch(endpoint);
        if (currentScope.isPresent()) {
            LOG.debug("Matched {} against {}", endpoint, currentScope);
            Object service = getEpListener();
            if (service instanceof EndpointEventListener) {
                notifyEEListener(event, currentScope.get(), (EndpointEventListener)service);
            } else if (service instanceof EndpointListener) {
                notifyEListener(event, currentScope.get(), (EndpointListener)service);
            }
        }
    }
    
    private Optional<String> getFirstMatch(EndpointDescription endpoint) {
        return scopes.stream().filter(endpoint::matches).findFirst();
    }

    private void notifyEEListener(EndpointEvent event, String currentScope, EndpointEventListener listener) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.info("Calling endpointchanged on class {} for filter {}, type {}, endpoint {} ", listener, currentScope, endpoint);
        listener.endpointChanged(event, currentScope);
    }
    
    private void notifyEListener(EndpointEvent event, String currentScope, EndpointListener listener) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.info("Calling old listener on class {} for filter {}, type {}, endpoint {} ", listener, currentScope, endpoint);
        switch (event.getType()) {
        case EndpointEvent.ADDED:
            listener.endpointAdded(endpoint, currentScope);
            break;

        case EndpointEvent.MODIFIED:
            listener.endpointRemoved(endpoint, currentScope);
            listener.endpointAdded(endpoint, currentScope);
            break;

        case EndpointEvent.REMOVED:
            listener.endpointRemoved(endpoint, currentScope);
            break;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((sref == null) ? 0 : sref.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Interest other = (Interest) obj;
        if (sref == null) {
            if (other.sref != null)
                return false;
        } else if (!sref.equals(other.sref))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Interest [scopes=" + scopes + ", epListener=" + epListener.getClass() + "]";
    }
    
}
