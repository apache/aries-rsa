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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.aries.rsa.core.event.EventProducer;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointPermission;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteServiceAdminCore implements RemoteServiceAdmin {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteServiceAdminCore.class);

    private final Map<Map<String, Object>, Collection<ExportRegistration>> exportedServices = new LinkedHashMap<>();
    private final Map<EndpointDescription, Collection<ImportRegistration>> importedServices = new LinkedHashMap<>();

    // marker stored in exportedServices while the export is in progress
    private final static List<ExportRegistration> EXPORT_IN_PROGRESS = Collections.emptyList();

    private final BundleContext bctx;
    private final EventProducer eventProducer;
    private DistributionProvider provider;
    private BundleContext apictx;
    private CloseHandler closeHandler;

    public RemoteServiceAdminCore(BundleContext context,
            BundleContext apiContext,
            EventProducer eventProducer,
            DistributionProvider provider) {
        this.bctx = context;
        this.apictx = apiContext;
        this.eventProducer = eventProducer;
        this.provider = provider;
        this.closeHandler = new CloseHandler() {
            public void onClose(ExportRegistration reg) {
                removeExportRegistration(reg);
                if (reg.getException() != null) {
                    return; // there is no reference to close, and an exception if we try getting it
                }
                ExportReference ref = reg.getExportReference();
                ServiceReference<?> sref = ref == null ? null : ref.getExportedService();
                Bundle bundle = sref == null ? null : sref.getBundle();
                BundleContext context = bundle == null ? null : bundle.getBundleContext();
                // the bundle/context may already be closed, e.g. when called from
                // the service factory ungetService when the bundle is stopped -
                // the context is already invalid at that point, and services are
                // automatically unregistered by the framework
                if (context != null) {
                    context.ungetService(sref);
                }
            }

            public void onClose(ImportRegistration importReg) {
                removeImportRegistration(importReg);
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ExportRegistration> exportService(ServiceReference sref, Map additionalProperties)
        throws IllegalArgumentException, UnsupportedOperationException {
        Map<String, Object> serviceProperties = getProperties(sref);
        if (additionalProperties != null) {
            overlayProperties(serviceProperties, additionalProperties);
        }

        List<String> interfaceNames = getInterfaceNames(serviceProperties);
        List<String> exportedConfigs = StringPlus.normalize(
            serviceProperties.get(RemoteConstants.SERVICE_EXPORTED_CONFIGS));
        List<String> configTypes = matchConfigTypes(exportedConfigs, true); // empty means pick one

        if (isImportedService(sref) || configTypes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> key = makeKey(serviceProperties);
        List<ExportRegistration> regs = getExistingOrLock(key, interfaceNames);
        if (regs == null) {
            try {
                regs = new ArrayList<>();
                for (String configType : configTypes) {
                    ExportRegistration reg = exportService(Arrays.asList(configType),
                        interfaceNames, sref, serviceProperties);
                    if (reg != null) {
                        regs.add(reg);
                    }
                }
                if (!regs.isEmpty()) {
                    store(key, regs);
                }
            } finally {
                unlock(key);
            }
        }
        return regs;
    }

    private void store(Map<String, Object> key, List<ExportRegistration> regs) {
        // enlist initial export registrations in global list of exportRegistrations
        synchronized (exportedServices) {
            exportedServices.put(key, new ArrayList<>(regs));
        }
        eventProducer.publishNotification(regs);
    }

    private void unlock(Map<String, Object> key) {
        synchronized (exportedServices) {
            exportedServices.remove(key, EXPORT_IN_PROGRESS);
            exportedServices.notifyAll(); // in any case, always notify waiting threads
        }
    }

    private List<ExportRegistration> getExistingOrLock(Map<String, Object> key, List<String> interfaces) {
        synchronized (exportedServices) {
            // check if it is already exported...
            Collection<ExportRegistration> regs = exportedServices.get(key);

            // if the export is already in progress, wait for it to be complete
            while (regs == EXPORT_IN_PROGRESS) {
                try {
                    exportedServices.wait();
                    regs = exportedServices.get(key);
                } catch (InterruptedException ie) {
                    LOG.debug("interrupted while waiting for export in progress");
                    return Collections.emptyList();
                }
            }

            // if the export is complete, return a copy of existing export
            if (regs != null) {
                LOG.debug("already exported this service. Returning existing registrations {} ", interfaces);
                return copyExportRegistration(regs);
            }

            // mark export as being in progress
            exportedServices.put(key, EXPORT_IN_PROGRESS);
        }
        return null;
    }

    private static BundleContext getBundleContext(ServiceReference<?> serviceReference) {
        Bundle serviceBundle = serviceReference.getBundle();
        if (serviceBundle == null) {
            throw new IllegalStateException("Service is already unregistered");
        }
        return serviceBundle.getBundleContext();
    }

    private ExportRegistration exportService(
            final List<String> configTypes,
            final List<String> interfaceNames,
            final ServiceReference<?> serviceReference,
            final Map<String, Object> serviceProperties) {
        LOG.info("interfaces selected for export: {}", interfaceNames);

        try {
            checkPermission(new EndpointPermission("*", EndpointPermission.EXPORT));
            final BundleContext serviceContext = getBundleContext(serviceReference);
            final Object serviceObject = serviceContext.getService(serviceReference); // unget it when export is closed
            if (serviceObject == null) {
                throw new IllegalStateException("service object is null (service was unregistered?)");
            }
            ExportRegistration reg = null;
            try {
                final Class<?>[] interfaces = getInterfaces(serviceObject, interfaceNames);
                final Map<String, Object> eprops = createEndpointProps(serviceProperties, configTypes, interfaces);

                Endpoint endpoint = AccessController.doPrivileged(
                    (PrivilegedAction<Endpoint>) () -> provider.exportService(
                        serviceObject, serviceContext, eprops, interfaces));
                if (endpoint != null) {
                    reg = new ExportRegistrationImpl(serviceReference, endpoint, closeHandler, eventProducer);
                }
                return reg;
            } finally {
                // if anything went wrong, don't leak the service reference
                if (reg == null) {
                    serviceContext.ungetService(serviceReference);
                }
            }
        } catch (IllegalArgumentException iae) {
            // TCK expects this for garbage input in config-type specific properties
            throw iae;
        } catch (Throwable t) {
            return new ExportRegistrationImpl(t, closeHandler, eventProducer);
        }
    }

    /**
     * Returns the interface classes corresponding to the given service's interface names.
     * The classes are returned in the same order as the given names.
     *
     * @param service the service implementing the interfaces
     * @param interfaceNames the interface names
     * @return the interface classes corresponding to the interface names
     * @throws ClassNotFoundException if the service does not implement any of the named interfaces
     */
    private Class<?>[] getInterfaces(Object service, List<String> interfaceNames) throws ClassNotFoundException {
        // prepare a map of all of the service's implemented interface names and classes
        Map<String, Class<?>> interfaces = new HashMap<>();
        for (Class<?> cls = service.getClass(); cls != null; cls = cls.getSuperclass()) {
            for (Class<?> interfaceClass : cls.getInterfaces()) {
                interfaces.put(interfaceClass.getName(), interfaceClass);
            }
        }
        // lookup the given names in order, ensuring all are found
        List<Class<?>> interfaceClasses = new ArrayList<>();
        for (String interfaceName : interfaceNames) {
            Class<?> interfaceClass = interfaces.get(interfaceName);
            if (interfaceClass == null) {
                throw new ClassNotFoundException("Service class " + service.getClass()
                    + " does not implement interface " + interfaceName);
            }
            interfaceClasses.add(interfaceClass);
        }
        return interfaceClasses.toArray(new Class[0]);
    }

    /**
     * Determines which interfaces should be exported.
     *
     * @param serviceProperties the exported service properties
     * @return the interfaces to be exported
     * @throws IllegalArgumentException if the service parameters are invalid
     * @see RemoteServiceAdmin#exportService
     * @see org.osgi.framework.Constants#OBJECTCLASS
     * @see RemoteConstants#SERVICE_EXPORTED_INTERFACES
     */
    private List<String> getInterfaceNames(Map<String, Object> serviceProperties) {
        List<String> providedInterfaces = StringPlus.normalize(serviceProperties.get(Constants.OBJECTCLASS));
        if (providedInterfaces == null || providedInterfaces.isEmpty()) {
            throw new IllegalArgumentException("service is missing the objectClass property");
        }

        List<String> exportedInterfaces
            = StringPlus.normalize(serviceProperties.get(RemoteConstants.SERVICE_EXPORTED_INTERFACES));
        if (exportedInterfaces == null || exportedInterfaces.isEmpty()) {
            throw new IllegalArgumentException("service is missing the service.exported.interfaces property");
        }

        List<String> interfaces = new ArrayList<>(1);
        if (exportedInterfaces.size() == 1 && "*".equals(exportedInterfaces.get(0))) {
            // FIXME: according to the spec, this should only return the interfaces, and not
            // non-interface classes (which are valid OBJECTCLASS values, even if discouraged)
            interfaces.addAll(providedInterfaces);
        } else {
            if (!providedInterfaces.containsAll(exportedInterfaces)) {
                throw new IllegalArgumentException(String.format(
                    "exported interfaces %s must be a subset of the service's registered types %s",
                        exportedInterfaces, providedInterfaces));
            }

            interfaces.addAll(exportedInterfaces);
        }
        return interfaces;
    }

    /**
     * Creates a unique key from the given exported service properties.
     * <p>
     * This key is used to store and lookup export registrations for a service,
     * and so determines when a service export already exists for the given
     * properties or whether a new one needs to be created.
     * <p>
     * The key must be comparable using the {@code equals} method so it can be used as
     * a map key. Specifically, array values are converted into lists so they can be compared.
     * <p>
     * Custom service properties that are not RSA/distribution properties are ignored
     * since they do not affect the export and should not cause a new endpoint to be
     * created. It is not well-defined which properties are unique to and endpoint and
     * which are not, but we do our best - currently we include the objectClass,
     * service.* and config-type prefixed properties in the key.
     *
     * @param properties a properties map
     * @return a map that represents the given map, but can be safely used as a map key itself
     */
    private Map<String, Object> makeKey(Map<String, Object> properties) {
        // FIXME: we should also make logically equal values actually compare as equal
        // (e.g. String+ values should be normalized)
        List<String> configTypes = StringPlus.normalize(properties.get(Constants.SERVICE_EXPORTED_CONFIGS));
        Map<String, Object> converted = new HashMap<>(properties.size());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String k = entry.getKey().toLowerCase(Locale.ROOT);
            // a heuristic for which service properties affect the exported endpoint and
            // which do not (and should reuse an existing endpoint and not create a new one)
            if (k.equals("objectclass") || k.startsWith("service.")
                   || (configTypes != null && configTypes.stream().anyMatch(k::startsWith))) {
                Object val = entry.getValue();
                // convert arrays into lists so that they can be compared via equals()
                if (val instanceof Object[]) {
                    val = Arrays.asList((Object[]) val);
                }
                converted.put(k, val);
            }
        }
        return converted;
    }

    private List<ExportRegistration> copyExportRegistration(Collection<ExportRegistration> regs) {
        Set<EndpointDescription> copiedEndpoints = new HashSet<>();

        // create a new list with copies of the exportRegistrations
        List<ExportRegistration> copy = new ArrayList<>(regs.size());
        for (ExportRegistration reg : regs) {
            ExportRegistrationImpl exportRegistrationImpl = (ExportRegistrationImpl) reg;
            ExportReference ref = reg.getException() != null ? null : reg.getExportReference();
            EndpointDescription endpoint = ref == null ? null : ref.getExportedEndpoint();
            // create one copy for each distinct endpoint description
            if (endpoint != null && copiedEndpoints.add(endpoint)) {
                copy.add(new ExportRegistrationImpl(exportRegistrationImpl));
                // also increase service reference count
                ServiceReference<?> sref = ref.getExportedService();
                BundleContext serviceContext = getBundleContext(sref);
                serviceContext.getService(sref); // unget it when export is closed
            }
        }

        regs.addAll(copy);

        eventProducer.publishNotification(copy);
        return copy;
    }

    private boolean isImportedService(ServiceReference<?> sref) {
        return sref.getProperty(RemoteConstants.SERVICE_IMPORTED) != null;
    }

    @Override
    public Collection<ExportReference> getExportedServices() {
        synchronized (exportedServices) {
            List<ExportReference> ers = new ArrayList<>();
            for (Collection<ExportRegistration> exportRegistrations : exportedServices.values()) {
                for (ExportRegistration er : exportRegistrations) {
                    if (er.getException() == null && er.getExportReference() != null) {
                        ers.add(er.getExportReference());
                    }
                }
            }
            return Collections.unmodifiableCollection(ers);
        }
    }

    @Override
    public Collection<ImportReference> getImportedEndpoints() {
        synchronized (importedServices) {
            List<ImportReference> irs = new ArrayList<>();
            for (Collection<ImportRegistration> irl : importedServices.values()) {
                for (ImportRegistration impl : irl) {
                    irs.add(impl.getImportReference());
                }
            }
            return Collections.unmodifiableCollection(irs);
        }
    }

    /**
     * Importing form here...
     */
    @Override
    public ImportRegistration importService(EndpointDescription endpoint) {
        LOG.debug("importService() Endpoint: {}", endpoint.getProperties());

        synchronized (importedServices) {
            Collection<ImportRegistration> imRegs = importedServices.get(endpoint);
            if (imRegs != null && !imRegs.isEmpty()) {
                LOG.debug("creating copy of existing import registrations");
                ImportRegistration irParent = imRegs.iterator().next();
                ImportRegistration ir = new ImportRegistrationImpl(irParent);
                imRegs.add(ir);
                eventProducer.publishNotification(ir);
                return ir;
            }

            if (matchConfigTypes(endpoint.getConfigurationTypes(), false).isEmpty()) {
                LOG.info("Ignoring endpoint {} as it has no compatible configuration types: {}",
                    endpoint.getId(), endpoint.getConfigurationTypes());
                return null;
            }

            // TODO: somehow select the interfaces that should be imported ---> job of the TopologyManager?
            List<String> matchingInterfaces = endpoint.getInterfaces();

            if (matchingInterfaces.isEmpty()) {
                LOG.info("No matching interfaces found for remote endpoint {}.", endpoint.getId());
                return null;
            }

            LOG.info("Importing service {} with interfaces {} using handler {}.",
                endpoint.getId(), endpoint.getInterfaces(), provider.getClass());

            ImportRegistrationImpl imReg = exposeServiceFactory(matchingInterfaces.toArray(new String[0]), endpoint, provider);
            if (imRegs == null) {
                imRegs = new ArrayList<>();
                importedServices.put(endpoint, imRegs);
            }
            imRegs.add(imReg);
            eventProducer.publishNotification(imReg);
            return imReg;
        }
    }

    private List<String> matchConfigTypes(List<String> types, boolean first) {
        List<String> matched = new ArrayList<>();
        Collections.addAll(matched, provider.getSupportedTypes());
        if (types == null || types.isEmpty()) {
            return matched.subList(0, first ? 1 : 0);
        }
        matched.retainAll(types);
        return matched;
    }

    protected ImportRegistrationImpl exposeServiceFactory(String[] interfaceNames,
                                            EndpointDescription endpoint,
                                            DistributionProvider handler) {
        ImportRegistrationImpl imReg = new ImportRegistrationImpl(endpoint, closeHandler, eventProducer);
        try {
            Dictionary<String, Object> serviceProps = new Hashtable<>(endpoint.getProperties());
            serviceProps.put(RemoteConstants.SERVICE_IMPORTED, true);
            serviceProps.remove(RemoteConstants.SERVICE_EXPORTED_INTERFACES);

            ClientServiceFactory csf = new ClientServiceFactory(endpoint, handler, imReg);

            // Export the factory using the api context as it has very few imports.
            // If the bundle publishing the factory does not import the service interface
            // package then the factory is visible for all consumers which we want.
            ServiceRegistration<?> csfReg = apictx.registerService(interfaceNames, csf, serviceProps);
            imReg.init(csf, csfReg);
        } catch (Exception ex) {
            // Only logging at debug level as this might be written to the log at the TopologyManager
            LOG.debug("Can not proxy service with interfaces {}: {}",
                Arrays.toString(interfaceNames), ex.getMessage(), ex);
            imReg.init(ex);
        }
        return imReg;
    }

    /**
     * Removes the provided Export Registration from the internal management structures.
     * This is called from the ExportRegistration itself when it is closed (so should
     * not attempt to close it again here).
     *
     * @param eri the export registration to remove
     */
    protected void removeExportRegistration(ExportRegistration eri) {
        synchronized (exportedServices) {
            for (Iterator<Collection<ExportRegistration>> it = exportedServices.values().iterator(); it.hasNext();) {
                Collection<ExportRegistration> value = it.next();
                for (Iterator<ExportRegistration> it2 = value.iterator(); it2.hasNext();) {
                    ExportRegistration er = it2.next();
                    if (er.equals(eri)) {
                        if (eri.getException() == null && eri.getExportReference() != null) {
                            eventProducer.notifyRemoval(eri.getExportReference());
                        }
                        it2.remove();
                        if (value.isEmpty()) {
                            it.remove();
                        }
                        return;
                    }
                }
            }
        }
    }

    protected void removeImportRegistration(ImportRegistration iri) {
        synchronized (importedServices) {
            LOG.debug("Removing importRegistration {}", iri);

            ImportReference importRef = iri.getException() != null ? null : iri.getImportReference();
            if (importRef == null) {
                return;
            }

            EndpointDescription endpoint = importRef.getImportedEndpoint();
            Collection<ImportRegistration> imRegs = importedServices.get(endpoint);
            if (imRegs != null && imRegs.contains(iri)) {
                imRegs.remove(iri);
                eventProducer.notifyRemoval(iri);
            }
            if (imRegs == null || imRegs.isEmpty()) {
                importedServices.remove(endpoint);
            }
        }
    }

    static void overlayProperties(Map<String, Object> serviceProperties,
                                  Map<String, Object> additionalProperties) {
        Map<String, String> keysLowerCase = new HashMap<>();
        for (String key : serviceProperties.keySet()) {
            keysLowerCase.put(key.toLowerCase(), key);
        }

        for (Map.Entry<String, Object> e : additionalProperties.entrySet()) {
            String key = e.getKey();
            String lowerKey = key.toLowerCase();
            if (Constants.SERVICE_ID.toLowerCase().equals(lowerKey)
                    || Constants.OBJECTCLASS.toLowerCase().equals(lowerKey)) {
                // objectClass and service.id must not be overwritten
                LOG.info("exportService called with additional properties map that contained illegal key: {}," +
                    " the key is ignored", key);
            } else {
                String origKey = keysLowerCase.get(lowerKey);
                if (origKey != null) {
                    LOG.debug("Overwriting property [{}] with value [{}]", origKey, e.getValue());
                } else {
                    origKey = key;
                    keysLowerCase.put(lowerKey, origKey);
                }
                serviceProperties.put(origKey, e.getValue());
            }
        }
    }

    /**
     * Returns a service's properties as a map.
     *
     * @param serviceReference a service reference
     * @return the service's properties as a map
     */
    private Map<String, Object> getProperties(ServiceReference<?> serviceReference) {
        String[] keys = serviceReference.getPropertyKeys();
        Map<String, Object> props = new HashMap<>(keys.length);
        for (String key : keys) {
            Object val = serviceReference.getProperty(key);
            props.put(key, val);
        }
        return props;
    }

    protected Map<String, Object> createEndpointProps(Map<String, Object> effectiveProps,
            List<String> configTypes, Class<?>[] ifaces) {
        Map<String, Object> props = effectiveProps.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("."))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        props.remove(Constants.SERVICE_ID);
        props.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, configTypes);
        String[] inames = Arrays.stream(ifaces).map(Class::getName).toArray(String[]::new);
        props.put(Constants.OBJECTCLASS, inames);
        props.put(RemoteConstants.ENDPOINT_SERVICE_ID, effectiveProps.get(Constants.SERVICE_ID));
        String frameworkUUID = bctx.getProperty(Constants.FRAMEWORK_UUID);
        props.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, frameworkUUID);
        for (Class<?> iface : ifaces) {
            String pkg = iface.getPackage().getName();
            String version = PackageUtil.getVersion(iface);
            props.put(RemoteConstants.ENDPOINT_PACKAGE_VERSION_ + pkg, version);
        }
        return props;
    }

    private void checkPermission(EndpointPermission permission) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(permission);
        }
    }
}
