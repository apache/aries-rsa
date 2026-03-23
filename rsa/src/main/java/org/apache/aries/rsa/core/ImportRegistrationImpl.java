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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.aries.rsa.core.event.EventProducer;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class ImportRegistrationImpl implements ImportRegistration, ImportReference {

    private static final Logger LOG = LoggerFactory.getLogger(ImportRegistrationImpl.class);

    private volatile Throwable exception;
    private volatile ServiceRegistration importedService; // used only in parent
    private EndpointDescription endpoint;
    private volatile ClientServiceFactory clientServiceFactory;
    private CloseHandler closeHandler;
    private AtomicBoolean closed = new AtomicBoolean();
    private AtomicBoolean closing = new AtomicBoolean();
    private boolean detached; // used only in parent

    private ImportRegistrationImpl parent;
    private List<ImportRegistrationImpl> children; // used only in parent

    private EventProducer eventProducer;

    /**
     * Constructs a new import registration.
     *
     * @param endpoint the imported endpoint info
     * @param closeHandler a callback function that will be invoked when the registration is closed
     * @param eventProducer an event producer that will be invoked when the endpoint is updated
     */
    public ImportRegistrationImpl(EndpointDescription endpoint, CloseHandler closeHandler, EventProducer eventProducer) {
        this.endpoint = endpoint;
        this.closeHandler = closeHandler;
        this.eventProducer = eventProducer;
        parent = this;
        children = new ArrayList<>(1);
    }

    /**
     * Constructs an import registration that is linked
     * (shares state) with the given import registration.
     *
     * @param ir the import registration that this instance is linked to
     */
    public ImportRegistrationImpl(ImportRegistration ir) {
        parent = ((ImportRegistrationImpl)ir).getParent();
        exception = parent.getException();
        endpoint = parent.getImportedEndpointDescription();
        clientServiceFactory = parent.clientServiceFactory;
        closeHandler = parent.closeHandler;

        parent.instanceAdded(this);
    }

    /**
     * Initializes this import registration in an error state.
     *
     * @param exception the exception that occurred during initialization
     */
    public void init(Throwable exception) {
        if (parent.exception != null || parent.clientServiceFactory != null || parent.importedService != null) {
            throw new IllegalStateException("already initialized");
        }
        this.exception = exception;
    }

    /**
     * Initializes this import registration in a successful state.
     *
     * @param csf  the {@link ClientServiceFactory} which is the implementation
     *             of the locally registered service which provides proxies to the
     *             remote imported service.
     * @param sreg the {@link ServiceRegistration} representing the locally
     *             registered {@link ClientServiceFactory} service which provides
     *             proxies to the remote imported service.
     */
    public void init(ClientServiceFactory csf, ServiceRegistration sreg) {
        if (parent.exception != null || parent.clientServiceFactory != null || parent.importedService != null) {
            throw new IllegalStateException("already initialized");
        }
        this.clientServiceFactory = csf;
        this.importedService = sreg;
    }

    private void ensureParent() {
        if (parent != this) {
            throw new IllegalStateException("this method may only be called on the parent");
        }
    }

    /**
     * Called on parent when a child is added.
     *
     * @param iri the child
     */
    private synchronized void instanceAdded(ImportRegistrationImpl iri) {
        ensureParent();
        children.add(iri);
    }

    /**
     * Called on parent when a child is closed.
     *
     * @param iri the child
     */
    private void instanceClosed(ImportRegistration iri) {
        ensureParent();
        synchronized (this) {
            children.remove(iri);
            if (!children.isEmpty() || detached || !closing.get()) {
                return;
            }
            detached = true;
        }

        LOG.debug("really closing ImportRegistration now");

        if (importedService != null) {
            try {
                importedService.unregister();
            } catch (IllegalStateException ise) {
                LOG.debug("imported service is already unregistered");
            }
            importedService = null;
        }
        if (clientServiceFactory != null) {
            clientServiceFactory.setCloseable(true);
        }
    }

    public void close() {
        LOG.debug("close() called");
        synchronized (this) {
            boolean curClosing = closing.getAndSet(true);
            if (curClosing || isInvalid()) {
                return;
            }
        }
        closeHandler.onClose(this);
        parent.instanceClosed(this);
        closed.set(true);
    }

    /**
     * Closes all ImportRegistrations which share the same parent as this one.
     */
    public void closeAll() {
        if (this == parent) {
            LOG.info("closing down all child ImportRegistrations");

            // we must iterate over a copy of children since close() removes the child
            // from the list (which would cause a ConcurrentModificationException)
            for (ImportRegistrationImpl ir : copyChildren()) {
                ir.close();
            }
            this.close();
        } else {
            parent.closeAll();
        }
    }

    private List<ImportRegistrationImpl> copyChildren() {
        synchronized (this) {
            return new ArrayList<>(children);
        }
    }

    public EndpointDescription getImportedEndpointDescription() {
        return isInvalid() ? null : endpoint;
    }

    @Override
    public EndpointDescription getImportedEndpoint() {
        return getImportedEndpointDescription();
    }

    @Override
    public ServiceReference<?> getImportedService() {
        return isInvalid() || parent.importedService == null ? null : parent.importedService.getReference();
    }

    @Override
    public ImportReference getImportReference() {
        return this;
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    private synchronized boolean isInvalid() {
        return exception != null || closed.get();
    }

    public ImportRegistrationImpl getParent() {
        return parent;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean update(EndpointDescription endpoint) {
        this.endpoint = endpoint;
        importedService.setProperties(new Hashtable(endpoint.getProperties()));
        eventProducer.notifyUpdate(this);
        return true;
    }
}
