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
package org.apache.aries.rsa.topologymanager.exporter;

import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;

/**
 * Wraps on old style EndpointListener into the new
 * EndpointEventListener interface
 */
@SuppressWarnings("deprecation")
public class EndpointListenerAdapter implements EndpointEventListener {
    private EndpointListener epl;

    public EndpointListenerAdapter(EndpointListener epl) {
        this.epl = epl;
    }

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        EndpointDescription endpoint = event.getEndpoint();
        switch (event.getType()) {
        case EndpointEvent.ADDED:
            epl.endpointAdded(endpoint, filter);
            break;
        case EndpointEvent.REMOVED:
            epl.endpointRemoved(endpoint, filter);
            break;
        case EndpointEvent.MODIFIED:
            epl.endpointRemoved(endpoint, filter);
            epl.endpointAdded(endpoint, filter);
            break;
        case EndpointEvent.MODIFIED_ENDMATCH:
            epl.endpointRemoved(endpoint, filter);
            break;
        }
    }
    
    /**
     * Checks for equality of the adapted EndpointListener
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EndpointListenerAdapter) {
            EndpointListenerAdapter ela = (EndpointListenerAdapter)obj;
            return epl.equals(ela.epl);
        } else {
            return super.equals(obj);
        }
    }
}
