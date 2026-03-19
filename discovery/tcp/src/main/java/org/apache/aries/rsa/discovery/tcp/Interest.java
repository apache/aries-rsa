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

import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE;

/**
 * An interest is a combination of an {@link EndpointEventListener} and its
 * published scope (i.e. the filters defining what endpoints it is interested in).
 * <p>
 * The {@code Interest} class acts as a gatekeeper for an {@code EndpointEventListener} -
 * it keeps track of its scopes, and when notified of endpoint events,
 * it forwards to the listener only those that match what it is interested in.
 */
public class Interest {
    private static final Logger LOG = LoggerFactory.getLogger(Interest.class);

    private final List<String> scopes;
    private final EndpointEventListener listener;

    public Interest(ServiceReference<?> sref, EndpointEventListener listener) {
        this.scopes = StringPlus.normalize(sref.getProperty(ENDPOINT_LISTENER_SCOPE));
        this.listener = listener;
    }

    public void notifyListener(EndpointEvent event) {
        EndpointDescription endpoint = event.getEndpoint();
        scopes.stream().filter(endpoint::matches).findFirst().ifPresent(scope -> { // notify with first scope
            LOG.info("Calling endpointChanged on {} for filter {}, type {}, endpoint {}",
                listener, scope, event.getType(), endpoint);
            listener.endpointChanged(event, scope);
        });
    }

    @Override
    public String toString() {
        return "Interest [scopes=" + scopes + ", listener=" + listener.getClass() + "]";
    }
}
