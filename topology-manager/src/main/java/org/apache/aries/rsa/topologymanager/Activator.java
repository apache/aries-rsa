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
package org.apache.aries.rsa.topologymanager;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.spi.ExportPolicy;
import org.apache.aries.rsa.topologymanager.exporter.DefaultExportPolicy;
import org.apache.aries.rsa.topologymanager.exporter.EndpointListenerNotifier;
import org.apache.aries.rsa.topologymanager.exporter.TopologyManagerExport;
import org.apache.aries.rsa.topologymanager.importer.TopologyManagerImport;
import org.apache.aries.rsa.topologymanager.importer.local.EndpointListenerManager;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Header;
import org.osgi.annotation.bundle.Requirement;
import org.osgi.annotation.bundle.Requirements;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Capability( //
        namespace = "osgi.remoteserviceadmin.topology", //
        attribute = {"policy:List<String>=promiscous"}, //
        version = "1.1.0"
)
@Requirements({
@Requirement(
        namespace = "osgi.remoteserviceadmin.discovery",
        filter = "(&(version>=1.0)(!(version>=2.0)))"
        ),
@Requirement(
        namespace = "osgi.remoteserviceadmin.distribution",
        filter = "(&(version>=1.0)(!(version>=2.0)))"
        )
})
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {
    public static final String RSA_EXPORT_POLICY_FILTER = "rsa.export.policy.filter";
    static final String DOSGI_SERVICES = "(" + RemoteConstants.SERVICE_EXPORTED_INTERFACES + "=*)";
    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    private TopologyManagerExport exportManager;
    private TopologyManagerImport importManager;
    EndpointListenerNotifier notifier;
    private ServiceTracker<RemoteServiceAdmin, RemoteServiceAdmin> rsaTracker;
    private ThreadPoolExecutor exportExecutor;
    
    private ServiceTracker<ExportPolicy, ExportPolicy> policyTracker;
    private EndpointListenerManager endpointListenerManager;
    private EndpointEventListenerTracker epeListenerTracker;

    public void start(final BundleContext bc) throws Exception {
        Dictionary<String, String> props = new Hashtable<>();
        props.put("name", "default");
        bc.registerService(ExportPolicy.class, new DefaultExportPolicy(), props);

        Filter policyFilter = exportPolicyFilter(bc);
        policyTracker = new ServiceTracker<ExportPolicy, ExportPolicy>(bc, policyFilter, null) {

            @Override
            public ExportPolicy addingService(ServiceReference<ExportPolicy> reference) {
                ExportPolicy policy = super.addingService(reference);
                if (exportManager == null) {
                    doStart(bc, policy);
                }
                return policy;
            }

            @Override
            public void removedService(ServiceReference<ExportPolicy> reference, ExportPolicy service) {
                if (exportManager != null) {
                    doStop(bc);
                }
                super.removedService(reference, service);
            }
        };
        policyTracker.open();
    }

    private Filter exportPolicyFilter(BundleContext bc) throws InvalidSyntaxException {
        String filter = bc.getProperty(RSA_EXPORT_POLICY_FILTER);
        if (filter == null) {
            filter = "(name=default)";
        }
        return FrameworkUtil.createFilter(String.format("(&(objectClass=%s)%s)", ExportPolicy.class.getName(), filter));
    }

    public void doStart(final BundleContext bc, ExportPolicy policy) {
        LOG.debug("TopologyManager: start()");
        notifier = new EndpointListenerNotifier();
        exportExecutor = new ThreadPoolExecutor(5, 10, 50, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        exportManager = new TopologyManagerExport(notifier, exportExecutor, policy);
        epeListenerTracker = new EndpointEventListenerTracker(bc, exportManager);
        importManager = new TopologyManagerImport(bc);
        endpointListenerManager = new EndpointListenerManager(bc, importManager);
        endpointListenerManager.start();
        rsaTracker = new RSATracker(bc, RemoteServiceAdmin.class, null);
        bc.addServiceListener(exportManager);
        rsaTracker.open();
        epeListenerTracker.open();
        exportExistingServices(bc);
        importManager.start();
    }

    public void stop(BundleContext bc) throws Exception {
        policyTracker.close();
    }

    public void doStop(BundleContext bc) {
        LOG.debug("TopologyManager: stop()");
        bc.removeServiceListener(exportManager);
        exportExecutor.shutdown();
        importManager.stop();
        endpointListenerManager.stop();
        rsaTracker.close();
        exportManager = null;
    }

    public void exportExistingServices(BundleContext context) {
        try {
            // cast to String is necessary for compiling against OSGi core version >= 4.3
            ServiceReference<?>[] references = context.getServiceReferences((String)null, DOSGI_SERVICES);
            if (references != null) {
                for (ServiceReference<?> sref : references) {
                    exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
                }
            }
        } catch (InvalidSyntaxException e) {
            LOG.error("Error in filter {}. This should not occur!", DOSGI_SERVICES);
        }
    }
    
    private final class RSATracker extends ServiceTracker<RemoteServiceAdmin, RemoteServiceAdmin> {
        private RSATracker(BundleContext context, Class<RemoteServiceAdmin> clazz,
                           ServiceTrackerCustomizer<RemoteServiceAdmin, RemoteServiceAdmin> customizer) {
            super(context, clazz, customizer);
        }

        @Override
        public RemoteServiceAdmin addingService(ServiceReference<RemoteServiceAdmin> reference) {
            RemoteServiceAdmin rsa = super.addingService(reference);
            LOG.debug("New RemoteServiceAdmin {} detected, trying to import and export services with it", rsa);
            importManager.add(rsa);
            exportManager.add(rsa);
            return rsa;
        }

        @Override
        public void removedService(ServiceReference<RemoteServiceAdmin> reference,
                                   RemoteServiceAdmin rsa) {
            exportManager.remove(rsa);
            importManager.remove(rsa);
            super.removedService(reference, rsa);
        }
    }
}
