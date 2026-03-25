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

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ServiceFactory} instance that supplies bundles each with their own RSA instance.
 * <p>
 * For every bundle, the first time it gets the service the factory's getService is invoked
 * to create the instance, and then the framework caches the instance and returns it when
 * the bundle subsequently calls get/unget while managing its reference count. Only when
 * the reference count reaches zero (the last time unget is called) is the factory's
 * ungetService invoked to destroy the instance.
 * <p>
 * We use a ServiceFactory according to the spec's recommendation, in order to implement
 * the requirement that if a TopologyManager bundle is stopped, all registrations that
 * were made by it must be closed, and when the RSA bundle is stopped all of its
 * registrations must be closed as well. In both cases, when using a factory, our
 * ungetService method will be invoked when either bundle is stopped, since the framework
 * automatically ungets all remaining services when stopped.
 */
public class RemoteServiceAdminFactory implements ServiceFactory<RemoteServiceAdmin> {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteServiceAdminFactory.class);

    private final RemoteServiceAdminCore rsaCore;

    public RemoteServiceAdminFactory(RemoteServiceAdminCore rsaCore) {
        this.rsaCore = rsaCore;
    }

    public synchronized RemoteServiceAdmin getService(Bundle b, ServiceRegistration<RemoteServiceAdmin> sreg) {
        LOG.debug("new RemoteServiceAdmin instance created for Bundle {}", b.getSymbolicName());
        return new RemoteServiceAdminInstance(b.getBundleContext(), rsaCore);
    }

    public synchronized void ungetService(Bundle b, ServiceRegistration<RemoteServiceAdmin> sreg,
                                          RemoteServiceAdmin serviceObject) {
        LOG.debug("RemoteServiceAdmin instance destroyed for Bundle {}", b.getSymbolicName());
        // close all registrations that were created by the calling bundle
        // (typically TopologyManager) when it is stopped, as required by the spec
        ((RemoteServiceAdminInstance)serviceObject).close();
    }
}
