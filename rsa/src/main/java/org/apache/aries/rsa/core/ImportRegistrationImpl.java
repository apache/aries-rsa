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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.aries.rsa.core.event.EventProducer;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an ImportRegistration. Since there is a 1:1 relationship between
 * an ImportRegistration and its ImportReference (they are basically two views
 * of the same underlying data - one is the modifiable part and one the read-only
 * part), this class implements both interfaces together.
 */
@SuppressWarnings("rawtypes")
public class ImportRegistrationImpl implements ImportRegistration, ImportReference {

    private static final Logger LOG = LoggerFactory.getLogger(ImportRegistrationImpl.class);

    /**
     * According to the specs, multiple ImportRegistrations can be linked together,
     * i.e. they have a shared state - this is implemented via this inner class,
     * where every group of linked import registrations reference a single Shared instance.
     */
    private static class Shared {

        private Set<CloseHandler> closeHandlers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private EventProducer eventProducer;
        private volatile EndpointDescription endpoint;
        private volatile Throwable exception;
        private volatile ClientServiceFactory clientServiceFactory;
        private volatile ServiceRegistration importedService;
        // all linked import registrations that share this state
        private final List<ImportRegistrationImpl> instances = new ArrayList<>(1);

        void addCloseHandler(CloseHandler closeHandler) {
            if (closeHandler != null) {
                closeHandlers.add(closeHandler);
            }
        }

        /**
         * Add a linked ImportRegistration instance to the shared data.
         *
         * @param ireg the instance to add
         */
        private synchronized void addInstance(ImportRegistrationImpl ireg) {
            instances.add(ireg);
        }

        /**
         * Remove a linked ImportRegistration from the shared data.
         *
         * @param ireg the instance to remove
         */
        private void removeInstance(ImportRegistrationImpl ireg) {
            // close the underlying service only once on the last remove (not before or after)
            synchronized (this) {
                boolean removed = instances.remove(ireg);
                if (!removed || !instances.isEmpty()) {
                    return;
                }
            }

            LOG.debug("closing ImportRegistration after removing last linked instance");
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

        void closeAll() {
            // make a copy to avoid ConcurrentModificationException
            // when instances are removed during iteration
            List<ImportRegistrationImpl> copy;
            synchronized (this) {
                copy = new ArrayList<>(instances);
            }
            LOG.info("closing all linked ImportRegistrations");
            for (ImportRegistrationImpl ireg : copy) {
                ireg.close();
            }
        }
    }

    // state shared between all linked import registrations in this instance's group
    private Shared shared;

    // per-instance state that is not shared
    private AtomicBoolean closing = new AtomicBoolean();
    private volatile boolean closed;

    /**
     * Constructs a new import registration that is not linked to any other instance.
     * <p>
     * The {@link #close} method must eventually be invoked on this instance.
     *
     * @param endpoint the imported endpoint info
     * @param closeHandler a callback function that will be invoked when the registration is closed
     * @param eventProducer an event producer that will be invoked when the endpoint is updated
     */
    public ImportRegistrationImpl(EndpointDescription endpoint, CloseHandler closeHandler, EventProducer eventProducer) {
        shared = new Shared();
        shared.endpoint = endpoint;
        shared.eventProducer = eventProducer;
        addCloseHandler(closeHandler);
        shared.addInstance(this);
    }

    /**
     * Constructs an import registration that is linked
     * (shares state) with the given import registration.
     * <p>
     * The {@link #close} method must eventually be invoked on this instance.
     *
     * @param ireg the import registration that this instance is linked to
     */
    public ImportRegistrationImpl(ImportRegistration ireg) {
        shared = ((ImportRegistrationImpl)ireg).shared;
        shared.addInstance(this);
    }

    private void init(ClientServiceFactory csf, ServiceRegistration sreg, Throwable exception) {
        if (isInvalid() || shared.clientServiceFactory != null || shared.importedService != null) {
            throw new IllegalStateException("already initialized");
        }
        shared.clientServiceFactory = csf;
        shared.importedService = sreg;
        shared.exception = exception;
    }

    /**
     * Initializes this import registration in an error state.
     *
     * @param exception the exception that occurred during initialization
     */
    public void init(Throwable exception) {
       init(null, null, exception);
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
        init(csf, sreg, null);
    }

    /**
     * Returns whether this registration is invalid
     * (has an initialization exception) or closed.
     *
     * @return whether this registration is invalid or closed
     */
    private boolean isInvalid() {
        return shared.exception != null || closed;
    }

    public void addCloseHandler(CloseHandler closeHandler) {
        shared.addCloseHandler(closeHandler);
    }

    /**
     * Closes this instance.
     * <p>
     * If this is the last instance in its group of linked instances,
     * the underlying import registration is also closed, otherwise
     * the import itself remains open for use by the other instances.
     */
    @Override
    public void close() {
        // we do this in two steps: first the 'closing' state to make sure we
        // proceed at most once (only if valid and not already closing/closed),
        // with all the references still valid while calling the close handler,
        // and only after it's done we reach the 'closed' state where all
        // getters return null (as required by the spec)
        LOG.debug("closing import registration");
        if (closed || closing.getAndSet(true)) {
            return;
        }
        shared.closeHandlers.forEach(h -> h.onClose(this));
        shared.removeInstance(this);
        closed = true;
    }

    /**
     * Closes this instance, all linked instances, and the underlying service.
     */
    public void closeAll() {
        shared.closeAll(); // invokes close() on all shared instances (including this one)
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean update(EndpointDescription endpoint) {
        if (isInvalid()) {
            throw new IllegalStateException("import registration is invalid or closed");
        }
        if (!endpoint.getId().equals(shared.endpoint.getId())) {
            throw new IllegalArgumentException("wrong endpoint id " + endpoint.getId());
        }
        shared.endpoint = endpoint;
        shared.importedService.setProperties(new Hashtable<>(endpoint.getProperties()));
        shared.eventProducer.notifyUpdate(this);
        return true;
    }

    @Override
    public ServiceReference<?> getImportedService() {
        return isInvalid() ? null : shared.importedService.getReference();
    }

    @Override
    public EndpointDescription getImportedEndpoint() {
        return isInvalid() ? null : shared.endpoint;
    }

    @Override
    public ImportReference getImportReference() {
        if (shared.exception != null) {
            throw new IllegalStateException("import registration is invalid");
        }
        return isInvalid() ? null : this; // this instance implements both interfaces
    }

    @Override
    public Throwable getException() {
        return closed ? null : shared.exception;
    }
}
