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

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.aries.rsa.core.event.EventProducer;
import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class ExportRegistrationImpl implements ExportRegistration {

    private static final Logger LOG = LoggerFactory.getLogger(ExportRegistrationImpl.class);

    /**
     * According to the specs, multiple ExportRegistration can be linked together,
     * i.e. they have a shared state - this is implemented via this inner class,
     * where every group of linked export registrations reference a single Shared instance.
     */
    private static class Shared {

        private Set<CloseHandler> closeHandlers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private EventProducer eventProducer;
        private Closeable server;
        private Throwable exception;

        private int instanceCount;

        void addCloseHandler(CloseHandler closeHandler) {
            if (closeHandler != null) {
                closeHandlers.add(closeHandler);
            }
        }

        void addInstance() {
            synchronized (this) {
                instanceCount++;
            }
        }

        void removeInstance() {
            synchronized (this) {
                if (--instanceCount != 0) {
                    return;
                }
                LOG.debug("closing ExportRegistration after removing last linked instance");
                if (server != null) {
                    try {
                        server.close();
                    } catch (IOException ioe) {
                        LOG.warn("Error closing ExportRegistration", ioe);
                    }
                }
            }
        }
    }

    // state shared between all linked export registrations in this instance's group
    private final Shared shared;

    // per-instance state that is not shared
    private volatile ExportReferenceImpl exportReference;
    private AtomicBoolean closing = new AtomicBoolean();
    private volatile boolean closed;


    /**
     * Constructs an export registration that is linked
     * (shares state) with the given export registration.
     * <p>
     * The {@link #close} method must eventually be invoked on this instance.
     *
     * @param er the export registration that this instance is linked to
     */
    public ExportRegistrationImpl(ExportRegistrationImpl er) {
        exportReference = new ExportReferenceImpl(er.exportReference);
        shared = er.shared;
        shared.addInstance();
    }

    /**
     * Constructs a new export registration that is not linked to any other instance
     * in a successful state.
     * <p>
     * The {@link #close} method must eventually be invoked on this instance.
     *
     * @param sref the exported service reference
     * @param endpoint the exported endpoint info
     * @param closeHandler a callback function that will be invoked when the registration is closed
     * @param eventProducer an event producer that will be invoked when the endpoint is updated
     */
    // create a new (parent) instance which was exported successfully with the given server
    public ExportRegistrationImpl(ServiceReference sref, Endpoint endpoint,
            CloseHandler closeHandler, EventProducer eventProducer) {
        exportReference = new ExportReferenceImpl(sref, endpoint.description());
        shared = new Shared();
        shared.eventProducer = eventProducer;
        shared.server = endpoint;
        addCloseHandler(closeHandler);
        shared.addInstance();
    }

    /**
     * Constructs a new export registration that is not linked to any other instance
     * in a failed state.
     * <p>
     * The {@link #close} method must eventually be invoked on this instance.
     *
     * @param exception the exception that occurred during initialization
     * @param closeHandler a callback function that will be invoked when the registration is closed
     * @param eventProducer an event producer that will be invoked when the endpoint is updated
     */
    public ExportRegistrationImpl(Throwable exception, CloseHandler closeHandler, EventProducer eventProducer) {
        shared = new Shared();
        shared.eventProducer = eventProducer;
        shared.exception = exception;
        addCloseHandler(closeHandler);
        shared.addInstance();
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

    @Override
    public ExportReference getExportReference() {
        if (closed) {
            return null;
        }
        if (shared.exception != null) {
            throw new IllegalStateException("export registration is invalid");
        }
        return exportReference;
    }

    @Override
    public Throwable getException() {
        return closed ? null : shared.exception;
    }

    public void addCloseHandler(CloseHandler closeHandler) {
        shared.addCloseHandler(closeHandler);
    }

    @Override
    public final void close() {
        // we do this in two steps: first the 'closing' state to make sure we
        // proceed at most once (only if valid and not already closing/closed),
        // with all the references still valid while calling the close handler,
        // and only after it's done we reach the 'closed' state where all
        // getters return null (as required by the spec)
        LOG.debug("closing export registration");
        if (closed || closing.getAndSet(true)) {
            return;
        }
        shared.closeHandlers.forEach(h -> h.onClose(this));
        if (exportReference != null) {
            exportReference.close();
        }
        shared.removeInstance();
        closed = true;
    }

    @Override
    public String toString() {
        if (closed) {
            return "ExportRegistration closed";
        }
        EndpointDescription endpoint = exportReference.getExportedEndpoint();
        ServiceReference serviceReference = exportReference.getExportedService();
        String s = "EndpointDescription for ServiceReference " + serviceReference;

        s += "\n*** EndpointDescription: ****\n";
        if (endpoint == null) {
            s += "---> NULL <---- \n";
        } else {
            Set<Map.Entry<String, Object>> props = endpoint.getProperties().entrySet();
            for (Map.Entry<String, Object> entry : props) {
                Object value = entry.getValue();
                s += entry.getKey() + " => "
                    + (value instanceof Object[] ? Arrays.toString((Object[]) value) : value) + "\n";
            }
        }
        return s;
    }

    @Override
    public EndpointDescription update(Map<String, ?> properties) {
        if (isInvalid()) {
            throw new IllegalStateException("export registration is invalid or closed");
        }

        Map<String, Object> oldProps = exportReference.getExportedEndpoint().getProperties();
        Map<String, Object> props = new HashMap<>(properties);
        props.putIfAbsent(RemoteConstants.ENDPOINT_ID, oldProps.get(RemoteConstants.ENDPOINT_ID));
        props.putIfAbsent(RemoteConstants.SERVICE_IMPORTED_CONFIGS, oldProps.get(RemoteConstants.SERVICE_IMPORTED_CONFIGS));

        ServiceReference<?> sref = exportReference.getExportedService();
        EndpointDescription endpoint = new EndpointDescription(sref, props);
        exportReference = new ExportReferenceImpl(sref, endpoint);
        shared.eventProducer.notifyUpdate(exportReference);
        return endpoint;
    }
}
