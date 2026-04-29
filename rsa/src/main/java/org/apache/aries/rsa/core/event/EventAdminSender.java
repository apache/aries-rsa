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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private BundleContext context;

    public EventAdminSender(BundleContext context) {
        this.context = context;
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
        String type = getConstName(RemoteServiceAdminEvent.class, null, rsae.getType(), "UNKNOWN_EVENT");
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

    /**
     * Returns the name of the first constant field (public static final) in the given class
     * whose value is equal to the given value and name starts with the given prefix,
     * or a default value if it is not found.
     *
     * @param cls the class containing the constant
     * @param prefix the constant name prefix (or null for any name)
     * @param value the constant value
     * @param defaultValue a default value to return if the constant is not found
     * @return the constant name, or the default value if it is not found
     */
    private static String getConstName(Class<?> cls, String prefix, Object value, String defaultValue) {
        for (Field f : cls.getDeclaredFields()) {
            try {
                int m = f.getModifiers();
                if (Modifier.isFinal(m) && Modifier.isStatic(m) && Modifier.isPublic(m)
                        && Objects.equals(f.get(null), value)
                        && (prefix == null || f.getName().startsWith(prefix))) {
                    return f.getName();
                }
            } catch (IllegalAccessException ignore) {
            }
        }
        return defaultValue;
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
