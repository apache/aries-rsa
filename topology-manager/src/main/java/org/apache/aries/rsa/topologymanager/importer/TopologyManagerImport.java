/**
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.BundleContext;
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
    private ExecutorService execService;

    private final BundleContext bctx;
    private Set<RemoteServiceAdmin> rsaSet;
    private boolean stopped;

    /**
     * List of Endpoints by matched filter that were reported by the EndpointListener and can be imported
     */
    private final MultiMap<EndpointDescription> importPossibilities = new MultiMap<>();

    /**
     * List of already imported Endpoints by their matched filter
     */
    private final MultiMap<ImportRegistration> importedServices = new MultiMap<>();
    
    public TopologyManagerImport(BundleContext bc) {
        this.rsaSet = new HashSet<>();
        bctx = bc;
        execService = new ThreadPoolExecutor(5, 10, 50, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }
    
    public void start() {
        stopped = false;
        bctx.registerService(RemoteServiceAdminListener.class, this, null);
    }

    public void stop() {
        stopped = true;
        execService.shutdown();
        try {
            execService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        closeAllImports();
    }

    private void closeAllImports() {
        importPossibilities.clear();
        for (String filter : importedServices.keySet()) {
            unImportForGoneEndpoints(filter);
        }
    }

    public void add(RemoteServiceAdmin rsa) {
        rsaSet.add(rsa);
        for (String filter : importPossibilities.keySet()) {
            triggerSynchronizeImports(filter);
        }
    }
    
    public void remove(RemoteServiceAdmin rsa) {
        rsaSet.remove(rsa);
    }

    @Override
    public void remoteAdminEvent(RemoteServiceAdminEvent event) {
        if (event.getType() == RemoteServiceAdminEvent.IMPORT_UNREGISTRATION) {
            unImport(event.getImportReference());
        }
    }

    private void triggerSynchronizeImports(final String filter) {
        LOG.debug("Import of a service for filter {} was queued", filter);
        if (!rsaSet.isEmpty()) {
            execService.execute(new Runnable() {
                public void run() {
                    synchronizeImports(filter);
                }
            });
        }
    }
    
    private void synchronizeImports(final String filter) {
        try {
            unImportForGoneEndpoints(filter);
            importServices(filter);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        // Notify EndpointListeners? NO!
    }

    private void importServices(String filter) {
        Set<ImportRegistration> importRegistrations = importedServices.get(filter);
        for (EndpointDescription endpoint : importPossibilities.get(filter)) {
            // TODO but optional: if the service is already imported and the endpoint is still
            // in the list of possible imports check if a "better" endpoint is now in the list
            if (!alreadyImported(endpoint, importRegistrations)) {
                ImportRegistration ir = importService(endpoint);
                if (ir != null) {
                    // import was successful
                    importedServices.put(filter, ir);
                }
            }
        }
    }

    private boolean alreadyImported(EndpointDescription endpoint, Set<ImportRegistration> importRegistrations) {
        for (ImportRegistration ir : importRegistrations) {
            if (endpoint.equals(ir.getImportReference().getImportedEndpoint())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to import the service with each rsa until one import is successful
     *
     * @param endpoint endpoint to import
     * @return import registration of the first successful import
     */
    private ImportRegistration importService(EndpointDescription endpoint) {
        for (RemoteServiceAdmin rsa : rsaSet) {
            ImportRegistration ir = rsa.importService(endpoint);
            if (ir != null) {
                if (ir.getException() == null) {
                    LOG.debug("Service import was successful {}", ir);
                    return ir;
                } else {
                    LOG.info("Error importing service " + endpoint, ir.getException());
                }
            }
        }
        return null;
    }

    private void unImportForGoneEndpoints(String filter) {
        Set<ImportRegistration> importRegistrations = importedServices.get(filter);
        Set<EndpointDescription> endpoints = importPossibilities.get(filter);
        for (ImportRegistration ir : importRegistrations) {
            EndpointDescription endpoint = ir.getImportReference().getImportedEndpoint();
            if (!endpoints.contains(endpoint)) {
                unImport(ir.getImportReference());
            }
        }
    }

    private void unImport(ImportReference ref) {
        List<ImportRegistration> removed = new ArrayList<>();
        HashSet<String> imported = new HashSet<>(importedServices.keySet());
        for (String key : imported) {
            for (ImportRegistration ir : importedServices.get(key)) {
                if (ir.getImportReference().equals(ref)) {
                    removed.add(ir);
                }
            }
        }
        closeAll(removed);
    }

    private void closeAll(List<ImportRegistration> removed) {
        for (ImportRegistration ir : removed) {
            importedServices.remove(ir);
            ir.close();
        }
    }

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        if (stopped) {
            return;
        }
        EndpointDescription endpoint = event.getEndpoint();
        LOG.debug("Endpoint event received type {}, filter {}, endpoint {}", event.getType(), filter, endpoint);
        switch (event.getType()) {
            case EndpointEvent.ADDED :
                importPossibilities.put(filter, endpoint);
                break;
            case EndpointEvent.REMOVED : 
                importPossibilities.remove(filter, endpoint);
                break;
            case EndpointEvent.MODIFIED :
                importPossibilities.remove(filter, endpoint);
                importPossibilities.put(filter, endpoint);
                break;
            case EndpointEvent.MODIFIED_ENDMATCH :
                importPossibilities.remove(filter, endpoint);
                break;
        }
        triggerSynchronizeImports(filter);
    }
    
}
