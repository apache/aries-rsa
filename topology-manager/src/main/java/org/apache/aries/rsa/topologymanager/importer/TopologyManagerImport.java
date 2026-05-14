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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
     * Provides an easier access to the ImportRegistration for a given EndpointDescription and filter, e.g. when an endpoint is removed or modified.
     */
    private final ConcurrentMap<EndpointDescriptionFilter, ImportRegistration> importedServices = new ConcurrentHashMap<>();

    private final Set<ImportReference> inProgressUnimports = ConcurrentHashMap.newKeySet();

    public TopologyManagerImport(BundleContext bc) {
        rsaSet = new CopyOnWriteArraySet<>();
        bctx = bc;

        // max 20, default=CPU-1, but minimum 2
        int poolSize = Math.max(2, Math.min(20, Runtime.getRuntime().availableProcessors() - 1));
        execService = new ThreadPoolExecutor(
                        poolSize,
                        poolSize,
                        10L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(),
                        new NamedThreadFactory(getClass())
        );
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
        // close all imports
        importPossibilities.clear();
        importedServices.values().forEach(this::unimportRegistration);
        importedServices.clear();
    }

    public void add(RemoteServiceAdmin rsa) {
        rsaSet.add(rsa);
        execService.execute(this::synchronizeImports);
    }

    public void remove(RemoteServiceAdmin rsa) {
        rsaSet.remove(rsa);
    }

    @Override
    public void remoteAdminEvent(RemoteServiceAdminEvent event) {
        ImportReference ref = event.getImportReference();
        if (event.getType() == RemoteServiceAdminEvent.IMPORT_UNREGISTRATION && ref != null) {
            if (inProgressUnimports.contains(ref)) {
                return;// no need to iterate over the imports
            }
            importedServices.values().stream()
                            .filter(ir -> ref.equals(ir.getImportReference()))
                            .forEach(this::unimportRegistration);
        }
    }

    private void synchronizeImports() {
        try {
            // remove all invalid imports and temporarily collect all filters and endpoints while at it for the imports below
            Map<String, Set<EndpointDescription>> validFiltersToImRegs = removeInvalidRegs();
            importAdded(validFiltersToImRegs);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * Imports all endpoints that are in the importPossibilities but not yet imported,
     * and that are still valid (e.g. not removed while the imports were being removed).
     * Used after a new RSA is added.
     *
     * @param validFiltersToImRegs a map of filters to the endpoints that are still valid imports after the removals,
     *                             used to avoid importing endpoints that were removed while the imports were being removed
     */
    private void importAdded(Map<String, Set<EndpointDescription>> validFiltersToImRegs) {
        // now import all new endpoints for each filter
        for (String filter : importPossibilities.keySet()) {
            Set<EndpointDescription> validEndpoints = validFiltersToImRegs.get(filter);
            for (EndpointDescription ed : importPossibilities.get(filter)) {
                // if the endpoint is not already imported for the filter, import it
                 if (validEndpoints == null || !validEndpoints.contains(ed)) {
                        // this is a new endpoint for the filter, import it
                        synchronizeAddedImport(filter, ed);
                 }
            }
        }
    }

    /**
     * Removes all imports that are no longer valid,
     * e.g. because the endpoint is no longer in the list of possible imports for the filter,
     * or because the import registration has no ImportReference (e.g. because the import failed).
     * Used after a new RSA is added.
     *
     * @return  a map of filters to the endpoints that are still valid imports, used for the imports after the removals
     */
    private Map<String, Set<EndpointDescription>> removeInvalidRegs() {
        Map<String, Set<EndpointDescription>> validFiltersToImRegs = new HashMap<>();
        importedServices.entrySet().stream()
                .filter(entry -> {
                // an import is invalid if the endpoint is no longer in the list of possible imports for the filter,
                // or if the import registration has no ImportReference (e.g. because the import failed)
                boolean invalid = !importPossibilities.get(entry.getKey().getFilter()).contains(entry.getKey().getEndpoint())
                                  || entry.getValue().getImportReference() == null;
                if (!invalid) {
                    validFiltersToImRegs.compute(entry.getKey().getFilter(), (filter, eds) -> {
                        if (eds == null) {
                            eds = new HashSet<>();
                        }
                        eds.add(entry.getKey().getEndpoint());
                        return eds;
                    });
                }
                return invalid;
            })
            .forEach(entry -> synchronizeRemovedImport(entry.getKey().getFilter(), entry.getKey().getEndpoint()));
        return validFiltersToImRegs;
    }

    private void synchronizeAddedImport(String filter, EndpointDescription endpoint) {
        if (!rsaSet.isEmpty()) {
            execService.execute(() -> importedServices.computeIfAbsent(new EndpointDescriptionFilter(filter, endpoint),
                                                  edFilter -> importService(filter, endpoint)));
        }
    }

    private void synchronizeRemovedImport(String filter, EndpointDescription endpoint) {
        if (!rsaSet.isEmpty()) {
            execService.execute(() -> {
                ImportRegistration importRegistration = importedServices.remove(new EndpointDescriptionFilter(filter, endpoint));
                if (importRegistration != null) {
                    unimportRegistration(importRegistration);
                }
            });
        }
    }

    private ImportRegistration importService(String filter, EndpointDescription endpoint) {
        for (RemoteServiceAdmin rsa : rsaSet) {
            ImportRegistration ir = rsa.importService(endpoint);
            if (ir != null) {
                if (ir.getException() == null) {
                    LOG.debug("Service import was successful for filter {}: {}", filter, ir);
                    return ir;
                } else {
                    LOG.info("Error importing service for filter {}: {}", filter, endpoint, ir.getException());
                }
            }
        }
        return null;
    }

    private void unimportRegistration(ImportRegistration reg) {
        // spares unnecessary iteration when the unimport event is received
        inProgressUnimports.add(reg.getImportReference());
        reg.close();
        inProgressUnimports.remove(reg.getImportReference());
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
                synchronizeAddedImport(filter, endpoint);
                break;
            case EndpointEvent.REMOVED:
            case EndpointEvent.MODIFIED_ENDMATCH:
                importPossibilities.remove(filter, endpoint);
                 synchronizeRemovedImport(filter, endpoint);
                break;
            case EndpointEvent.MODIFIED:
                importPossibilities.remove(filter, endpoint);
                synchronizeRemovedImport(filter, endpoint);
                importPossibilities.put(filter, endpoint);
                synchronizeAddedImport(filter, endpoint);
                break;
        }
    }

}
