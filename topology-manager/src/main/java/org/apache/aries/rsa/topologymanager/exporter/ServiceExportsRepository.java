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
import java.util.stream.Collectors;

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
    // holds the set of exports for each service, and also caches the endpoint associated with each export,
    // so we'll be able to access it even after it becomes unreachable via the export when it is closed
    private final Map<ServiceReference<?>, Map<ExportRegistration, EndpointDescription>> exports = new LinkedHashMap<>();

    public ServiceExportsRepository(RemoteServiceAdmin rsa, EndpointListenerNotifier notifier) {
        this.rsa = rsa;
        this.notifier = notifier;
    }

    private static Map<String, Object> getServiceProps(ServiceReference<?> sref) {
        HashMap<String, Object> props = new HashMap<>();
        for (String key : sref.getPropertyKeys()) {
            props.put(key, sref.getProperty(key));
        }
        return props;
    }

    @Override
    public synchronized void close() {
        LOG.debug("Closing registry for RemoteServiceAdmin {}", rsa.getClass().getName());
        new ArrayList<>(exports.keySet()).forEach(this::removeService); // iterate over copy to avoid CME
    }

    public synchronized void addService(ServiceReference<?> sref, Collection<ExportRegistration> registrations) {
        Map<ExportRegistration, EndpointDescription> regs = new LinkedHashMap<>();
        for (ExportRegistration reg : registrations) {
            ExportReference ref = reg.getExportReference();
            EndpointDescription endpoint = ref == null ? null : ref.getExportedEndpoint();
            if (endpoint != null) {
                regs.put(reg, endpoint);
                notifier.sendEvent(new EndpointEvent(EndpointEvent.ADDED, endpoint));
            }
        }
        if (!regs.isEmpty()) {
            exports.put(sref, regs);
        }
    }

    public synchronized void modifyService(ServiceReference<?> sref) {
        Map<ExportRegistration, EndpointDescription> regs = exports.get(sref);
        if (regs != null) {
            Map<String, ?> props = getServiceProps(sref);
            for (Map.Entry<ExportRegistration, EndpointDescription> entry: regs.entrySet()) {
                EndpointDescription updated = entry.getKey().update(props);
                if (updated != null) {
                    entry.setValue(updated);
                    notifier.sendEvent(new EndpointEvent(EndpointEvent.MODIFIED, updated));
                }
            }
        }
    }

    public synchronized void removeService(ServiceReference<?> sref) {
        Map<ExportRegistration, EndpointDescription> regs = exports.remove(sref);
        if (regs != null) {
            regs.forEach((reg, endpoint) -> {
                notifier.sendEvent(new EndpointEvent(EndpointEvent.REMOVED, endpoint));
                reg.close();
            });
        }
    }

    public synchronized List<EndpointDescription> getAllEndpoints() {
        return exports.values().stream()
            .flatMap(regs -> regs.keySet().stream())
            .map(ExportRegistration::getExportReference)
            .filter(Objects::nonNull)
            .map(ExportReference::getExportedEndpoint)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
