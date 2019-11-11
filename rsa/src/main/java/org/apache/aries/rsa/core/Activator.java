/**
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

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

@org.osgi.annotation.bundle.Capability( //
        namespace = "osgi.service", //
        attribute = {"objectClass:List<String>=org.osgi.service.remoteserviceadmin.RemoteServiceAdmin"}, //
        uses = { RemoteServiceAdmin.class}
)
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

    private DistributionProviderTracker tracker;

    public void start(BundleContext bundlecontext) throws Exception {
        tracker = new DistributionProviderTracker(bundlecontext);
        tracker.open();
    }

    public void stop(BundleContext context) throws Exception {
        tracker.close();
    }

}
