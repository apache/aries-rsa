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
package org.apache.aries.rsa.topologymanager.importer.local;

import java.util.Collection;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RSFindHook implements FindHook {
    private static final Logger LOG = LoggerFactory.getLogger(RSFindHook.class);
    
    private BundleContext bctx;
    private String frameworkUUID;
    private ServiceInterestListener serviceInterestListener;

    public RSFindHook(BundleContext bc, ServiceInterestListener serviceInterestListener) {
        this.bctx = bc;
        this.frameworkUUID = bctx.getProperty(Constants.FRAMEWORK_UUID);
        this.serviceInterestListener = serviceInterestListener;
    }

    @Override
    public void find(BundleContext context, String name, String filter, boolean allServices,
                     Collection<ServiceReference<?>> references) {
        if (context.equals(bctx)) {
            LOG.debug("ListenerHookImpl: skipping request from myself");
            return;
        }
        
        String fullFilter = FilterHelper.getFullFilter(name, filter);
        
        if (fullFilter == null) {
            LOG.debug("skipping empty filter");
            return;
        }
        String className = name != null ? name : FilterHelper.getObjectClass(fullFilter);
        if (FilterHelper.isClassExcluded(className)) {
            LOG.debug("Skipping import request for excluded class [{}]", className);
            return;
        }
        String exFilter = excludeLocalServices(fullFilter);
        serviceInterestListener.addServiceInterest(exFilter);
    }

    String excludeLocalServices(String filter) {
        return "(&" + filter + "(!(" + RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + frameworkUUID + ")))";
    }
}
