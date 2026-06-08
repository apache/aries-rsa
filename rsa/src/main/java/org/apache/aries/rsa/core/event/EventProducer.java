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
package org.apache.aries.rsa.core.event;

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
    private EventAdminSender eventAdminSender;

    public EventProducer(BundleContext bc) {
        bctx = bc;
        eventAdminSender = new EventAdminSender(bc);
    }

    public void publishNotification(List<ExportRegistration> ereg) {
        for (ExportRegistration exportRegistration : ereg) {
            publishNotification(exportRegistration);
        }
    }

    protected void publishNotification(ExportRegistration ereg) {
        if (ereg.getException() == null) {
            notify(RemoteServiceAdminEvent.EXPORT_REGISTRATION, ereg.getExportReference(), null);
        } else {
            notify(RemoteServiceAdminEvent.EXPORT_ERROR, (ExportReference) null, ereg.getException());
        }
    }

    public void publishNotification(ImportRegistration ireg) {
        if (ireg.getException() == null) {
            notify(RemoteServiceAdminEvent.IMPORT_REGISTRATION, ireg.getImportReference(), null);
        } else {
            notify(RemoteServiceAdminEvent.IMPORT_ERROR, (ImportReference) null, ireg.getException());
        }
    }

    public void notifyRemoval(ExportReference eref) {
        notify(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION, eref, null);
    }

    public void notifyRemoval(ImportRegistration ireg) {
        notify(RemoteServiceAdminEvent.IMPORT_UNREGISTRATION, ireg.getImportReference(), null);
    }

    private void notify(int type, ExportReference eref, Throwable ex) {
        try {
            RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(type, bctx.getBundle(), eref, ex);
            notifyListeners(event);
        } catch (IllegalStateException ise) {
            LOG.debug("can't send notifications since bundle context is no longer valid");
        }
    }
    private void notify(int type, ImportReference iref, Throwable ex) {
        try {
            RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(type, bctx.getBundle(), iref, ex);
            notifyListeners(event);
        } catch (IllegalStateException ise) {
            LOG.debug("can't send notifications since bundle context is no longer valid");
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void notifyListeners(RemoteServiceAdminEvent rsae) {
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
                                LOG.debug("notify RemoteServiceAdminListener {} of bundle {} (type {})",
                                    rsal, bundle.getSymbolicName(), rsae.getType());
                                rsal.remoteAdminEvent(rsae);
                            }
                        } catch (RuntimeException re) {
                            LOG.error("unexpected exception when notifying {} of {}", rsal, rsae, re);
                        } finally {
                            bctx.ungetService(sref);
                        }
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            LOG.error(e.getMessage(), e);
        }
        eventAdminSender.send(rsae);
    }

    public void notifyUpdate(ExportReference eref) {
        notify(RemoteServiceAdminEvent.EXPORT_UPDATE, eref, null);
    }

    public void notifyUpdate(ImportRegistration ireg) {
        notify(RemoteServiceAdminEvent.IMPORT_UPDATE, ireg.getImportReference(), ireg.getException());
    }
}
