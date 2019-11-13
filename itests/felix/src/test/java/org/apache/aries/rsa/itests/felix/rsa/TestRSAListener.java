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
package org.apache.aries.rsa.itests.felix.rsa;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;

@RunWith(PaxExam.class)
public class TestRSAListener extends RsaTestBase implements RemoteServiceAdminListener {
    private static final int EVENT_TIMEOUT = 2000;
    private RemoteServiceAdminEvent lastEvent;
    private Bundle serviceBundle;
    
    @Inject
    EchoService echoService;
    
    @Inject
    RemoteServiceAdmin rsa;
    
    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] //
        {
         rsaCore(), //
         rsaProviderTcp(), //
         echoTcpService(), //
         configFastBinFreePort()
        };
    }

    @Test
    public void testListener() throws Exception {
        serviceBundle = getBundle("org.apache.aries.rsa.examples.echotcp.service");

        serviceBundle.stop();
        ServiceRegistration<RemoteServiceAdminListener> sreg = bundleContext.registerService(RemoteServiceAdminListener.class, this, null);
        
        serviceBundle.start();
        assertEvent(RemoteServiceAdminEvent.EXPORT_REGISTRATION);
        
        Thread.sleep(1000);
        
        serviceBundle.stop();
        assertEvent(RemoteServiceAdminEvent.EXPORT_UNREGISTRATION);

        sreg.unregister();
    }

    @Override
    public synchronized void remoteAdminEvent(RemoteServiceAdminEvent event) {
        if (event.getExportReference() != null && serviceBundle == event.getExportReference().getExportedService().getBundle()) {
            lastEvent = event;
            this.notifyAll();
        }
    }

    private void assertEvent(int eventType) throws InterruptedException, TimeoutException {
        waitEvent();
        assertEquals(eventType, lastEvent.getType());
        this.lastEvent = null;
    }

    private synchronized void waitEvent() throws InterruptedException, TimeoutException {
        this.wait(EVENT_TIMEOUT);
        if (this.lastEvent == null) {
            throw new TimeoutException("Timeout waiting for Event");
        }
    }

}
