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
package org.apache.aries.rsa.discovery.config;

import java.util.Hashtable;

import org.apache.aries.rsa.spi.discovery.InterestManager;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedServiceFactory;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
@org.osgi.annotation.bundle.Capability( //
        namespace = "osgi.remoteserviceadmin.discovery", //
        attribute = {"configs:List<String>=config"}, //
        version = "1.1.0"
)
public class Activator implements BundleActivator {
    private static final String FACTORY_PID = "org.apache.aries.rsa.discovery.config";

    private InterestManager interestManager;
    private ServiceRegistration<ManagedServiceFactory> registration;

    public void start(BundleContext context) {
        interestManager = new InterestManager();
        ConfigDiscovery configDiscovery = new ConfigDiscovery(interestManager);
        interestManager.start(context, null);
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, FACTORY_PID);
        registration = context.registerService(ManagedServiceFactory.class, configDiscovery, props);
    }

    public void stop(BundleContext context) {
        registration.unregister();
        interestManager.stop();
    }
}
