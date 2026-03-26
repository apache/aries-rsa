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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.aries.rsa.core.event.EventProducer;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an ExportRegistration. Since there is a 1:1 relationship between
 * an ExportRegistration and its ExportReference (they are basically two views
 * of the same underlying data - one is the modifiable part and one the read-only
 * part), this class implements both interfaces together.
 */
@SuppressWarnings("rawtypes")
public class ExportRegistrationImpl implements ExportRegistration, ExportReference {

    private static final Logger LOG = LoggerFactory.getLogger(ExportRegistrationImpl.class);

    /**
     * According to the specs, multiple ExportRegistration can be linked together,
     * i.e. they have a shared state - this is implemented via this inner class,
     * where every group of linked export registrations reference a single Shared instance.
     */
    private static class Shared {

        private Set<CloseHandler> closeHandlers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private EventProducer eventProducer;
        private ServiceReference serviceReference;
        private volatile EndpointDescription endpoint;
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
        shared = new Shared();
        shared.eventProducer = eventProducer;
        shared.serviceReference = sref;
        shared.endpoint = endpoint.description();
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
    public EndpointDescription getExportedEndpoint() {
        return isInvalid() ? null : shared.endpoint;
    }

    @Override
    public ServiceReference<?> getExportedService() {
        return isInvalid() ? null : shared.serviceReference;
    }

    @Override
    public ExportReference getExportReference() {
        if (closed) {
            return null;
        }
        if (shared.exception != null) {
            throw new IllegalStateException("export registration is invalid");
        }
        return this; // this instance implements both interfaces
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
        shared.removeInstance();
        closed = true;
    }

    @Override
    public String toString() {
        if (closed) {
            return "ExportRegistration closed";
        }

        String s = "EndpointDescription for ServiceReference " + shared.serviceReference;
        s += "\n*** EndpointDescription: ****\n";
        if (shared.endpoint == null) {
            s += "---> NULL <---- \n";
        } else {
            Set<Map.Entry<String, Object>> props = shared.endpoint.getProperties().entrySet();
            for (Map.Entry<String, Object> entry : props) {
                Object value = entry.getValue();
                s += entry.getKey() + " => "
                    + (value instanceof Object[] ? Arrays.toString((Object[]) value) : value) + "\n";
            }
        }
        return s;
    }

    /**
     * Creates an updated set of endpoint properties by merging some
     * of the old endpoint properties with updated service properties.
     * <p>
     * Endpoint properties include a mix of the regular service properties
     * (unrelated to RSA), RSA-related properties that are included
     * in the service properties (such as which provider/config to use
     * for export), and RSA and distribution provider specific properties
     * that are not included in the service properties but are added
     * to the endpoint (or removed) during the export process (such as a
     * url used by the provider to remotely connect to the endpoint).
     * <p>
     * When the original service properties are updated, we get notified
     * with a copy of the new service properties, but without any
     * RSA/provider changes or additions (since they did not go through
     * a new export process). Original service properties may have been
     * added, removed, or have modified values in the new service properties.
     * <p>
     * When we update an endpoint, we thus need to update the original service
     * properties, but we must preserve the original RSA/provider properties
     * in order for the updated endpoint to continue being usable by RSA.
     * <p>
     * There is no clear definition of how to determine which endpoint properties
     * are service properties to be updated/added/removed and which are
     * RSA/provider properties to be preserved, but a heuristic based on the
     * property name prefix seems to be a reasonable solution.
     *
     * @param oldEndpointProps the properties from the existing (pre-update) endpoint
     * @param newServiceProps the new service properties (without RSA/distribution additions)
     * @return the merged set of old RSA/distribution properties with new custom properties
     */
    private Map<String, Object> merge(Map<String, Object> oldEndpointProps, Map<String, ?> newServiceProps) {
        List<String> configTypes = StringPlus.normalize(oldEndpointProps.get(Constants.SERVICE_IMPORTED_CONFIGS));
        // from old props, add only properties we think are RSA/distribution/framework related
        Map<String, Object> props = oldEndpointProps.entrySet().stream()
            .filter(e -> {
                String k = e.getKey().toLowerCase(Locale.ROOT); // case-insensitive
                return k.startsWith("endpoint.") || k.startsWith("service.") || k.startsWith("osgi.basic.")
                    || k.equals("objectclass") || configTypes.stream().anyMatch(k::startsWith);
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // from new service properties, add all public properties
        // (plus override any rsa/distribution properties that may be present)
        newServiceProps.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("."))
            .forEach(e -> props.put(e.getKey(), e.getValue()));
        return props;
    }

    @Override
    public EndpointDescription update(Map<String, ?> properties) {
        if (isInvalid()) {
            throw new IllegalStateException("export registration is invalid or closed");
        }
        EndpointDescription endpoint = shared.endpoint;
        Map<String, Object> props = merge(endpoint.getProperties(), properties);
        endpoint = new EndpointDescription(props);
        shared.endpoint = endpoint;
        shared.eventProducer.notifyUpdate(this);
        return endpoint;
    }
}
