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

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.ImportedService;
import org.apache.aries.rsa.spi.IntentUnsatisfiedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class ClientServiceFactory implements ServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ClientServiceFactory.class);

    private EndpointDescription endpoint;
    private DistributionProvider handler;
    private ImportRegistrationImpl importRegistration;

    private boolean closeable;
    private Map<Object, ImportedService> services = new HashMap<>();

    public ClientServiceFactory(EndpointDescription endpoint,
                                DistributionProvider handler, ImportRegistrationImpl ir) {
        this.endpoint = endpoint;
        this.handler = handler;
        this.importRegistration = ir;
    }

    public Object getService(final Bundle requestingBundle, final ServiceRegistration sreg) {
        List<String> interfaceNames = endpoint.getInterfaces();
        final BundleContext consumerContext = requestingBundle.getBundleContext();
        final ClassLoader consumerLoader = requestingBundle.adapt(BundleWiring.class).getClassLoader();
        try {
            LOG.debug("getService() from serviceFactory for {}", interfaceNames);
            final List<Class<?>> interfaces = new ArrayList<>();
            for (String ifaceName : interfaceNames) {
                interfaces.add(consumerLoader.loadClass(ifaceName));
            }
            ImportedService importedService = AccessController.doPrivileged(new PrivilegedAction<ImportedService>() {
                public ImportedService run() {
                    Class<?>[] ifAr = interfaces.toArray(new Class[]{});
                    return handler.importEndpoint(consumerLoader, consumerContext, ifAr, endpoint);
                }
            });

            Object proxy = importedService.getService();
            synchronized (this) {
                services.put(proxy, importedService);
            }
            return proxy;
        } catch (IntentUnsatisfiedException iue) {
            LOG.info("Did not create proxy for {} because intent {} could not be satisfied",
                    interfaceNames, iue.getIntent());
        } catch (Exception e) {
            LOG.warn("Problem creating a remote proxy for {}", interfaceNames, e);
        }
        return null;
    }

    public void ungetService(Bundle requestingBundle, ServiceRegistration sreg, Object serviceObject) {
        synchronized (this) {
            ImportedService importedService = services.remove(serviceObject);
            if (importedService != null) {
                try {
                    importedService.close();
                } catch (IOException e) {
                    LOG.warn("Problem closing imported service proxy {} for {}", serviceObject, requestingBundle, e);
                }
            }
            LOG.debug("Services still provided by this ServiceFactory: {}", services.size());
            closeIfUnused();
        }
    }

    public void setCloseable(boolean closeable) {
        synchronized (this) {
            this.closeable = closeable;
            closeIfUnused();
        }
    }

    private synchronized void closeIfUnused() {
        if (services.isEmpty() && closeable) {
            importRegistration.closeAll();
        }
    }
}
