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
package org.apache.aries.rsa.topologymanager.importer;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for remote endpoints using the EndpointListener. The scope of this listener is managed by
 * the EndpointListenerManager.
 * Manages local creation and destruction of service imports using the available RemoteServiceAdmin services.
 */
public class TopologyManagerImport implements EndpointEventListener, RemoteServiceAdminListener {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyManagerImport.class);

    private final ExecutorService execService;
    private final BundleContext bctx;
    private final Set<RemoteServiceAdmin> rsaSet;
    private ServiceRegistration<RemoteServiceAdminListener> rsaListenerRegistration;
    private volatile boolean stopped;

    /**
     * List of Endpoints by matched filter that were reported by the EndpointListener and can be imported
     */
    private final MultiMap<String, EndpointDescription> importPossibilities = new MultiMap<>();

    /**
     * List of already imported Endpoints by their matched filter
     */
    private final MultiMap<String, ImportRegistration> importedServices = new MultiMap<>();

    public TopologyManagerImport(BundleContext bc) {
        rsaSet = new CopyOnWriteArraySet<>();
        bctx = bc;
        execService = Executors.newCachedThreadPool(new NamedThreadFactory(getClass()));
    }

    public void start() {
        stopped = false;
        rsaListenerRegistration = bctx.registerService(RemoteServiceAdminListener.class, this, null);
    }

    public void stop() {
        stopped = true;
        if (rsaListenerRegistration != null) {
            rsaListenerRegistration.unregister();
        }
        execService.shutdown();
        try {
            execService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.info("Interrupted while waiting for {} to terminate", execService);
            Thread.currentThread().interrupt();
        }
        execService.shutdownNow();
        // close all imports
        importPossibilities.clear();
        importedServices.allValues().forEach(this::unimportRegistration);
    }

    public void add(RemoteServiceAdmin rsa) {
        rsaSet.add(rsa);
        importPossibilities.keySet().forEach(this::synchronizeImportsAsync);
    }

    public void remove(RemoteServiceAdmin rsa) {
        rsaSet.remove(rsa);
    }

    @Override
    public void remoteAdminEvent(RemoteServiceAdminEvent event) {
        ImportReference ref = event.getImportReference();
        if (event.getType() == RemoteServiceAdminEvent.IMPORT_UNREGISTRATION && ref != null) {
            importedServices.allValues().stream()
                .filter(reg -> ref.equals(reg.getImportReference()))
                .forEach(this::unimportRegistration);
        }
    }

    private void synchronizeImportsAsync(final String filter) {
        LOG.debug("Import of a service for filter {} was queued", filter);
        if (!rsaSet.isEmpty()) {
            execService.execute(() -> synchronizeImports(filter));
        }
    }

    /**
     * Synchronizes the actual imports with the possible imports for the given filter,
     * i.e. un-imports previously imported endpoints that are no longer valid or possible,
     * and imports new possible endpoints that are not already imported.
     * <p>
     * TODO but optional: if the service is already imported and the endpoint is still
     * in the list of possible imports check if a "better" endpoint is now in the list.
     *
     * @param filter the filter whose endpoints are synchronized
     */
    private void synchronizeImports(final String filter) {
        try {
            // we have a set of all current imports, and a set of all possible imports (with overlap)
            Set<ImportRegistration> imported = importedServices.get(filter);
            Set<EndpointDescription> possibleSet = importPossibilities.get(filter);
            Map<EndpointDescription, EndpointDescription> possible = possibleSet.stream()
                .collect(Collectors.toMap(e -> e, e -> e)); // convert to map for getting the value
            // first we iterate over all current imports, and split them into three groups:
            // - still valid (no null references) and possible (in possible set)
            // - still valid and possible but with changed properties
            // - invalid (contain null references) or no longer possible (not in possible set)
            // note that this part should be concurrency-safe (get every reference only once and don't modify anything)
            Set<EndpointDescription> valid = new HashSet<>(); // imports that are still valid and possible
            Set<ImportRegistration> invalid = new LinkedHashSet<>(); // imports that are no longer valid/possible
            Map<ImportRegistration, EndpointDescription> updated = new LinkedHashMap<>(); // valid with changed props
            for (ImportRegistration reg : imported) {
                ImportReference ref = reg.getImportReference();
                EndpointDescription endpoint = ref == null ? null : ref.getImportedEndpoint();
                // check if the currently imported endpoint is still valid and possible
                EndpointDescription pe = possible.get(endpoint); // get the new (maybe modified) possible endpoint
                if (pe != null) {
                    if (getChangedProps(endpoint.getProperties(), pe.getProperties()).isEmpty())
                        valid.add(endpoint); // valid and possible
                    else
                        updated.put(reg, pe); // valid and possible and changed properties
                } else {
                    invalid.add(reg); // invalid (reg or ref or endpoint is null) or no longer possible
                }
            }
            // now that we figured out what needs to be done, apply the changes to each group
            invalid.forEach(this::unimportRegistration); // remove invalid/non-possible imports
            updated.forEach((reg, e) -> { // update modified properties for existing imports
                try {
                    reg.update(e);
                } catch (IllegalStateException ise) {
                    LOG.warn("can't update closed endpoint {}", e, ise);
                }
            });
            possible.keySet().forEach(endpoint -> { // import all possible endpoints that are not already imported
                if (!valid.contains(endpoint) && !updated.containsValue(endpoint)) {
                    importService(filter, endpoint);
                }
            });
        } catch (Exception e) {
            LOG.error("error synchronizing imports", e);
        }
        // Notify EndpointListeners? NO!
    }

    /**
     * Tries to import the service with each RSA until one import is successful.
     *
     * @param filter the filter that matched the endpoint
     * @param endpoint endpoint to import
     */
    private void importService(String filter, EndpointDescription endpoint) {
        for (RemoteServiceAdmin rsa : rsaSet) {
            ImportRegistration reg = rsa.importService(endpoint);
            if (reg != null) {
                if (reg.getException() == null) {
                    LOG.debug("Service import was successful {}", reg);
                    importedServices.put(filter, reg);
                    return;
                } else {
                    LOG.warn("Error importing service {}", endpoint, reg.getException());
                    reg.close();
                }
            }
        }
    }
    
    private void unimportRegistration(ImportRegistration reg) {
        importedServices.remove(reg);
        reg.close();
    }

    private static Set<String> getChangedProps(Map<String, Object> p1, Map<String, Object> p2) {
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : p1.entrySet()) {
            Object v = p2.get(entry.getKey());
            if (!Objects.deepEquals(entry.getValue(), v) || v == null && !p2.containsKey(entry.getKey()))
                changed.add(entry.getKey());
        }
        for (String k : p2.keySet()) {
            if (!p1.containsKey(k))
                changed.add(k);
        }
        return changed;
    }

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        if (stopped) {
            return;
        }
        EndpointDescription endpoint = event.getEndpoint();
        LOG.debug("Endpoint event received type {}, filter {}, endpoint {}", event.getType(), filter, endpoint);
        switch (event.getType()) {
            case EndpointEvent.ADDED:
                importPossibilities.put(filter, endpoint);
                break;
            case EndpointEvent.REMOVED:
            case EndpointEvent.MODIFIED_ENDMATCH:
                importPossibilities.remove(filter, endpoint);
                break;
            case EndpointEvent.MODIFIED:
                // new endpoint has same endpoint.id and equals old endpoint, but has updated properties
                importPossibilities.replace(filter, endpoint, endpoint);
                break;
        }
        synchronizeImportsAsync(filter);
    }

}
