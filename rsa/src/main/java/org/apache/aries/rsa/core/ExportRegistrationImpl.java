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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    private final CloseHandler closeHandler;
    private ExportReferenceImpl exportReference;
    private final Closeable server;
    private final Throwable exception;

    private final ExportRegistrationImpl parent;
    private int instanceCount;
    private volatile boolean closed;

    private EventProducer sender;

    private ExportRegistrationImpl(ExportRegistrationImpl parent, 
            CloseHandler rsaCore,
            EventProducer sender,
            ExportReferenceImpl exportReference, 
            Closeable server, 
            Throwable exception) {
        this.sender = sender;
        this.parent = parent != null ? parent.parent : this; // a parent points to itself
        this.parent.addInstance();
        this.closeHandler = rsaCore;
        this.exportReference = exportReference;
        this.server = server;
        this.exception = exception;
    }

    // create a clone of the provided ExportRegistrationImpl that is linked to it
    public ExportRegistrationImpl(ExportRegistrationImpl parent) {
        this(parent, parent.closeHandler, parent.sender, new ExportReferenceImpl(parent.exportReference),
            parent.server, parent.exception);
    }

    // create a new (parent) instance which was exported successfully with the given server
    public ExportRegistrationImpl(ServiceReference sref, Endpoint endpoint, CloseHandler closeHandler, EventProducer sender) {
        this(null, closeHandler, sender, new ExportReferenceImpl(sref, endpoint.description()), endpoint, null);
    }

    // create a new (parent) instance which failed to be exported with the given exception
    public ExportRegistrationImpl(Throwable exception, CloseHandler closeHandler, EventProducer sender) {
        this(null, closeHandler, sender, null, null, exception);
    }

    private void ensureParent() {
        if (parent != this) {
            throw new IllegalStateException("this method may only be called on the parent");
        }
    }

    /**
     * Returns the ExportReference even if this
     * instance is closed or has an exception.
     *
     * @return the export reference
     */
    public ExportReference getExportReferenceAlways() {
        return exportReference;
    }

    public ExportReference getExportReference() {
        if (closed) {
            return null;
        } else {
            if (exportReference == null) {
                throw new IllegalStateException(getException());
            } else {
                return exportReference;
            }
        }
    }

    public Throwable getException() {
        return closed ? null : exception;
    }

    public final void close() {
        closeHandler.onClose(this);
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }

        if (exportReference != null) {
            exportReference.close();
        }
        parent.removeInstance();
    }

    private void addInstance() {
        ensureParent();
        synchronized (this) {
            instanceCount++;
        }
    }

    private void removeInstance() {
        ensureParent();
        synchronized (this) {
            instanceCount--;
            if (instanceCount <= 0) {
                LOG.debug("really closing ExportRegistration now!");
                closeServer();
            }
        }
    }

    private void closeServer() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                LOG.warn("Error closing ExportRegistration", e);
            }
        }
    }

    @Override
    public String toString() {
        if (closed) {
            return "ExportRegistration closed";
        }
        EndpointDescription endpoint = getExportReference().getExportedEndpoint();
        ServiceReference serviceReference = getExportReference().getExportedService();
        String r = "EndpointDescription for ServiceReference " + serviceReference;

        r += "\n*** EndpointDescription: ****\n";
        if (endpoint == null) {
            r += "---> NULL <---- \n";
        } else {
            Set<Map.Entry<String, Object>> props = endpoint.getProperties().entrySet();
            for (Map.Entry<String, Object> entry : props) {
                Object value = entry.getValue();
                r += entry.getKey() + " => "
                    + (value instanceof Object[] ? Arrays.toString((Object[]) value) : value) + "\n";
            }
        }
        return r;
    }

    @Override
    public EndpointDescription update(Map<String, ?> properties) {
        if (getExportReference() == null) {
            return null;
        }
        ServiceReference<?> sref = getExportReference().getExportedService();
        
        HashMap<String, Object> props = new HashMap<>(properties);
        EndpointDescription oldEpd = getExportReference().getExportedEndpoint();
        copyIfNull(props, oldEpd, RemoteConstants.ENDPOINT_ID);
        copyIfNull(props, oldEpd, RemoteConstants.SERVICE_IMPORTED_CONFIGS);

        EndpointDescription epd = new EndpointDescription(sref, props);
        exportReference = new ExportReferenceImpl(sref, epd);
        this.sender.notifyUpdate(this.getExportReference());
        return epd;
    }

    private void copyIfNull(HashMap<String, Object> props, EndpointDescription oldEpd, String key) {
        if (props.get(key) == null) {
            props.put(key, oldEpd.getProperties().get(key));
        }
    }
}
