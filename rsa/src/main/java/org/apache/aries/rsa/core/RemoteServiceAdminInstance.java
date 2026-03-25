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
package org.apache.aries.rsa.core;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointPermission;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

/**
 * An RSA instance that is created and returned by the {@link RemoteServiceAdminFactory},
 * one per calling bundle. All instances delegate the heavy lifting to a shared
 * RSA core implementation, but each instance keeps track of the registrations that
 * were performed through it so that it can later close them when the corresponding bundle
 * (TopologyManager) is closed.
 */
public class RemoteServiceAdminInstance implements RemoteServiceAdmin, CloseHandler {

    // Context of the bundle that is using this the RemoteServiceAdmin (i.e. the TopologyManager)
    private final BundleContext context;
    private final RemoteServiceAdminCore rsaCore;
    private final List<ExportRegistration> exportRegistrations = new CopyOnWriteArrayList<>();
    private final List<ImportRegistration> importRegistrations = new CopyOnWriteArrayList<>();

    public RemoteServiceAdminInstance(BundleContext context, RemoteServiceAdminCore rsaCore) {
        this.context = context;
        this.rsaCore = rsaCore;
    }

    @Override
    public List<ExportRegistration> exportService(final ServiceReference ref, final Map properties) {
        List<ExportRegistration> regs = rsaCore.exportService(ref, properties);
        // we need to keep track of our open registrations, and be notified when they are closed
        regs.forEach(reg -> ((ExportRegistrationImpl)reg).addCloseHandler(this));
        exportRegistrations.addAll(regs);
        return regs;
    }

    @Override
    public Collection<ExportReference> getExportedServices() {
        checkPermission(new EndpointPermission("*", EndpointPermission.READ));
        return rsaCore.getExportedServices();
    }

    @Override
    public Collection<ImportReference> getImportedEndpoints() {
        checkPermission(new EndpointPermission("*", EndpointPermission.READ));
        return rsaCore.getImportedEndpoints();
    }

    @Override
    public ImportRegistration importService(final EndpointDescription endpoint) {
        String frameworkUUID = context.getProperty(Constants.FRAMEWORK_UUID);
        checkPermission(new EndpointPermission(endpoint, frameworkUUID, EndpointPermission.IMPORT));
        ImportRegistration reg = AccessController.doPrivileged((PrivilegedAction<ImportRegistration>)
            () -> rsaCore.importService(endpoint));
        if (reg != null) {
            // we need to keep track of our open registrations, and be notified when they are closed
            ((ImportRegistrationImpl)reg).addCloseHandler(this);
            importRegistrations.add(reg);
        }
        return reg;
    }

    /**
     * Close all registrations that were made through this RSA instance.
     */
    public void close() {
        importRegistrations.forEach(ImportRegistration::close);
        exportRegistrations.forEach(ExportRegistration::close);
    }

    private void checkPermission(EndpointPermission permission) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(permission);
        }
    }

    @Override
    public void onClose(ExportRegistration reg) {
        exportRegistrations.remove(reg); // registration was closed, no need to track it anymore
    }

    @Override
    public void onClose(ImportRegistration reg) {
        importRegistrations.remove(reg); // registration was closed, no need to track it anymore
    }
}
