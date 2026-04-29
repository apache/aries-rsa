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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;

public class EventAdminSender {
    private HashMap<Integer, String> typeToTopic;
    private BundleContext context;

    public EventAdminSender(BundleContext context) {
        this.context = context;
        typeToTopic = new HashMap<>();
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_ERROR, "EXPORT_ERROR");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_REGISTRATION, "EXPORT_REGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION, "EXPORT_UNREGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_UPDATE, "EXPORT_UPDATE");
        typeToTopic.put(RemoteServiceAdminEvent.EXPORT_WARNING, "EXPORT_WARNING");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_ERROR, "IMPORT_ERROR");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_REGISTRATION, "IMPORT_REGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_UNREGISTRATION, "IMPORT_UNREGISTRATION");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_UPDATE, "IMPORT_UPDATE");
        typeToTopic.put(RemoteServiceAdminEvent.IMPORT_WARNING, "IMPORT_WARNING");
    }

    private void notifyEventAdmins(Event event) {
        ServiceReference<EventAdmin> sref = this.context.getServiceReference(EventAdmin.class);
        if (sref != null) {
            final EventAdmin eventAdmin = this.context.getService(sref);
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    eventAdmin.postEvent(event);
                    return null;
                }
            });
            this.context.ungetService(sref);
        }
    }

    public void send(RemoteServiceAdminEvent rsae) {
        String type = typeToTopic.get(rsae.getType());
        String topic = "org/osgi/service/remoteserviceadmin/" + type;
        Map<String, Object> props = createProps(rsae);
        Event event = new Event(topic, props);
        notifyEventAdmins(event);
    }

    private static <K, V> void putIfNotNull(Map<K, V> map, K key, V val) {
        if (val != null) {
            map.put(key, val);
        }
    }

    private Map<String, Object> createProps(RemoteServiceAdminEvent rsae) {
        Map<String, Object> props = new HashMap<>();
        // bundle properties
        Bundle bundle = rsae.getSource();
        props.put("bundle", bundle);
        props.put("bundle.id", bundle.getBundleId());
        props.put("bundle.symbolicname", bundle.getSymbolicName());

        String version = bundle.getHeaders().get("Bundle-Version");
        Version v = version != null ? new Version(version) : Version.emptyVersion;
        putIfNotNull(props, "bundle.version", v);

        Map<X509Certificate, List<X509Certificate>> signers = bundle.getSignerCertificates(Bundle.SIGNERS_ALL);
        if (signers != null) {
            String[] names = signers.keySet().stream()
                    .map(cert -> cert.getSubjectX500Principal().getName())
                    .filter(s -> s != null && !s.isEmpty())
                    .toArray(String[]::new);
            if (names.length > 0) {
                props.put("bundle.signer", names);
            }
        }

        // exception properties
        Throwable exception = rsae.getException();
        if (exception != null) {
            props.put("exception", exception);
            props.put("exception.class", exception.getClass().getName());
            putIfNotNull(props, "exception.message", exception.getMessage());
        }

        // endpoint properties
        ImportReference importReference = rsae.getImportReference();
        ExportReference exportReference = rsae.getExportReference();
        EndpointDescription endpoint = importReference == null ? null : importReference.getImportedEndpoint();
        endpoint = endpoint == null && exportReference != null ? exportReference.getExportedEndpoint() : endpoint;
        if (endpoint != null) {
            putIfNotNull(props, "endpoint.service.id", endpoint.getServiceId());
            putIfNotNull(props, "endpoint.framework.uuid", endpoint.getFrameworkUUID());
            putIfNotNull(props, "endpoint.id", endpoint.getId());
            props.put("objectClass", endpoint.getInterfaces().toArray(new String[0]));
            putIfNotNull(props, "service.imported.configs", endpoint.getConfigurationTypes());
        }

        // general properties
        props.put("timestamp", System.currentTimeMillis());
        props.put("event", rsae);

        return props;
    }
}
