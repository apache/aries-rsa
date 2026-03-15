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

import java.io.Closeable;
import java.util.*;

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds all Exports of a given RemoteServiceAdmin.
 */
public class ServiceExportsRepository implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceExportsRepository.class);

    private final RemoteServiceAdmin rsa;
    private final EndpointListenerNotifier notifier;
    private final Map<ServiceReference<?>, Collection<ExportRegistrationHolder>> exportsMap = new LinkedHashMap<>();

    /**
     * The holder allows us to work around that registration.getReference() is null when the registration is closed.
     */
    private class ExportRegistrationHolder {
        private final ExportRegistration registration;
        private final ExportReference reference;
        private EndpointDescription endpoint;

        ExportRegistrationHolder(ExportRegistration registration, EndpointDescription endpoint) {
            this.registration = registration;
            this.reference = registration.getExportReference();
            this.endpoint = endpoint;
            notifier.sendEvent(new EndpointEvent(EndpointEvent.ADDED, endpoint));
        }

        ExportRegistration getRegistration() {
            return registration;
        }

        void close() {
            if (reference != null) {
                notifier.sendEvent(new EndpointEvent(EndpointEvent.REMOVED, endpoint));
                registration.close();
            }
        }

        public void update(ServiceReference<?> sref) {
            if (reference != null) {
                endpoint = registration.update(getServiceProps(sref));
                notifier.sendEvent(new EndpointEvent(EndpointEvent.MODIFIED, endpoint));
            }
        }

        private Map<String, ?> getServiceProps(ServiceReference<?> sref) {
            HashMap<String, Object> props = new HashMap<>();
            for (String key : sref.getPropertyKeys()) {
                props.put(key, sref.getProperty(key));
            }
            return props;
        }
    }

    public ServiceExportsRepository(RemoteServiceAdmin rsa, EndpointListenerNotifier notifier) {
        this.rsa = rsa;
        this.notifier = notifier;
    }

    @Override
    public synchronized void close() {
        LOG.debug("Closing registry for RemoteServiceAdmin {}", rsa.getClass().getName());
        for (ServiceReference<?> sref : new ArrayList<>(exportsMap.keySet())) { // iterate over copy to avoid CME
            removeService(sref);
        }
    }

    public synchronized void addService(ServiceReference<?> sref, Collection<ExportRegistration> registrations) {
        Collection<ExportRegistrationHolder> exports = new ArrayList<>(registrations.size());
        for (ExportRegistration reg : registrations) {
            ExportReference reference = reg.getExportReference();
            if (reference != null) {
                exports.add(new ExportRegistrationHolder(reg, reference.getExportedEndpoint()));
            }
        }
        if (!exports.isEmpty()) {
            exportsMap.put(sref, exports);
        }
    }

    public synchronized void modifyService(ServiceReference<?> sref) {
        Collection<ExportRegistrationHolder> exports = exportsMap.get(sref);
        if (exports != null) {
            for (ExportRegistrationHolder reg : exports) {
                reg.update(sref);
            }
        }
    }

    public synchronized void removeService(ServiceReference<?> sref) {
        Collection<ExportRegistrationHolder> exports = exportsMap.remove(sref);
        if (exports != null) {
            for (ExportRegistrationHolder reg : exports) {
                reg.close();
            }
        }
    }

    public synchronized List<EndpointDescription> getAllEndpoints() {
        List<EndpointDescription> endpoints = new ArrayList<>();
        for (Collection<ExportRegistrationHolder> exports : exportsMap.values()) {
            for (ExportRegistrationHolder reg : exports) {
                ExportReference reference = reg.getRegistration().getExportReference();
                if (reference != null) {
                    endpoints.add(reference.getExportedEndpoint());
                }
            }
        }
        return endpoints;
    }
}
