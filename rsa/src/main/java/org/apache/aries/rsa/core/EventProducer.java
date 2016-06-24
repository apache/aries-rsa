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
package org.apache.aries.rsa.core;

import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventProducer.class);
    private final BundleContext bctx;

    public EventProducer(BundleContext bc) {
        bctx = bc;
    }

    protected void publishNotification(List<ExportRegistration> erl) {
        for (ExportRegistration exportRegistration : erl) {
            publishNotification(exportRegistration);
        }
    }

    protected void publishNotification(ExportRegistration er) {
        if (er.getException() == null) {
            notify(RemoteServiceAdminEvent.EXPORT_REGISTRATION, er.getExportReference(), null);
        } else {
            notify(RemoteServiceAdminEvent.EXPORT_ERROR, (ExportReference) null, er.getException());
        }
    }

    protected void publishNotification(ImportRegistration ir) {
        if (ir.getException() == null) {
            notify(RemoteServiceAdminEvent.IMPORT_REGISTRATION, ir.getImportReference(), null);
        } else {
            notify(RemoteServiceAdminEvent.IMPORT_ERROR, (ImportReference) null, ir.getException());
        }
    }

    public void notifyRemoval(ExportReference er) {
        notify(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION, er, null);
    }

    public void notifyRemoval(ImportRegistration ir) {
        notify(RemoteServiceAdminEvent.IMPORT_UNREGISTRATION, ir.getImportReference(), null);
    }

    private void notify(int type, ExportReference er, Throwable ex) {
        try {
            RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(type, bctx.getBundle(), er, ex);
            notifyListeners(event);
        } catch (IllegalStateException ise) {
            LOG.debug("can't send notifications since bundle context is no longer valid");
        }
    }
    private void notify(int type, ImportReference ir, Throwable ex) {
        try {
            RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(type, bctx.getBundle(), ir, ex);
            notifyListeners(event);
        } catch (IllegalStateException ise) {
            LOG.debug("can't send notifications since bundle context is no longer valid");
        }
    }

    @SuppressWarnings({
     "rawtypes", "unchecked"
    })
    private void notifyListeners(RemoteServiceAdminEvent rsae) {
        try {
            ServiceReference[] listenerRefs = bctx.getServiceReferences(
                    RemoteServiceAdminListener.class.getName(), null);
            if (listenerRefs != null) {
                for (ServiceReference sref : listenerRefs) {
                    RemoteServiceAdminListener rsal = (RemoteServiceAdminListener)bctx.getService(sref);
                    if (rsal != null) {
                        try {
                            Bundle bundle = sref.getBundle();
                            if (bundle != null) {
                                LOG.debug("notify RemoteServiceAdminListener {} of bundle {}",
                                        rsal, bundle.getSymbolicName());
                                rsal.remoteAdminEvent(rsae);
                            }
                        } finally {
                            bctx.ungetService(sref);
                        }
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
