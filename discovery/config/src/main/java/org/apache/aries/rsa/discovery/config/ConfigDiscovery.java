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
package org.apache.aries.rsa.discovery.config;

import org.apache.aries.rsa.spi.discovery.InterestManager;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.remoteserviceadmin.EndpointDescription;

import java.util.*;

/**
 * Monitors adding/updating/removing of configuration instances under the factory pid,
 * as well as adding/removing of EndpointEventListeners, and triggers EndpointEvents
 * accordingly as endpoints are created/updated/removed (via the configuration changes).
 */
class ConfigDiscovery implements ManagedServiceFactory {
    private final InterestManager interestManager;

    public ConfigDiscovery() {
        this.interestManager = new InterestManager();
    }

    @Override
    public String getName() {
        return "Aries RSA Config Discovery";
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) {
        EndpointDescription endpoint = new EndpointDescription(PropertyValidator.validate(properties));
        interestManager.addEndpoint(pid, endpoint);
    }

    @Override
    public void deleted(String pid) {
        interestManager.removeSource(pid);
    }

    public InterestManager getInterestManager() {
        return interestManager;
    }
}
