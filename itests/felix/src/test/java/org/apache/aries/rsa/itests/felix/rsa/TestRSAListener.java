package org.apache.aries.rsa.itests.felix.rsa;
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


import static org.junit.Assert.*;

import javax.inject.Inject;

import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;

@RunWith(PaxExam.class)
public class TestRSAListener extends RsaTestBase implements RemoteServiceAdminListener {

    @Inject
    BundleContext context;

    private RemoteServiceAdminEvent lastEvent;


    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCoreZookeeper(),
                configZKServer(),
                rsaFastBin(),
                echoTcpService(),
                configFastBin("2545"),
                configZKConsumer()
        };
    }

    @Test
    public void testListener() throws Exception {

        Thread.sleep(1000);
        context.registerService(RemoteServiceAdminListener.class, this, null);
        Bundle serviceBundle = null;
        Bundle[] bundles = context.getBundles();
        for (Bundle bundle : bundles) {
            if("org.apache.aries.rsa.examples.echotcp.service".equals(bundle.getSymbolicName())) {
                serviceBundle = bundle;
                break;
            }
        }
        serviceBundle.stop();
        assertNotNull(lastEvent);
        assertEquals(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION, lastEvent.getType());
        assertNotNull("ExportReference must be available",lastEvent.getExportReference());

        serviceBundle.start();
        Thread.sleep(3000);
        assertNotNull(lastEvent);
        assertEquals(RemoteServiceAdminEvent.EXPORT_REGISTRATION, lastEvent.getType());
        assertNotNull("ExportReference must be available",lastEvent.getExportReference());
    }

    @Override
    public void remoteAdminEvent(RemoteServiceAdminEvent event) {
        lastEvent = event;
    }

}
