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
package org.apache.aries.rsa.topologymanager.importer;

import static org.easymock.EasyMock.*;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;

public class TopologyManagerImportTest {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private BundleContext mockBundleContext(IMocksControl c) {
        ServiceRegistration sreg = c.createMock(ServiceRegistration.class);
        sreg.unregister();
        BundleContext bc = c.createMock(BundleContext.class);
        expect(bc.registerService(eq(RemoteServiceAdminListener.class),
                anyObject(RemoteServiceAdminListener.class),
                anyObject())).andReturn(sreg).once();
        return bc;
    }

    private ImportRegistration mockImportRegistration(IMocksControl c, EndpointDescription endpoint) {
        final ImportRegistration ireg = c.createMock(ImportRegistration.class);
        expect(ireg.getException()).andReturn(null).anyTimes();
        expect(ireg.update(anyObject())).andReturn(true).anyTimes();
        ImportReference iref = c.createMock(ImportReference.class);
        expect(ireg.getImportReference()).andReturn(iref).anyTimes();
        expect(iref.getImportedEndpoint()).andReturn(endpoint).anyTimes();
        return ireg;
    }

    private EndpointDescription createEndpoint() {
        EndpointDescription endpoint = c.createMock(EndpointDescription.class);
        final ImportRegistration ir = mockImportRegistration(c, endpoint);
        ir.close(); // must be closed
        expectLastCall().andAnswer(() -> {
            endpoints.get(endpoint).decrementAndGet();
            return null;
        });
        expect(rsa.importService(eq(endpoint))).andAnswer(() -> {
            endpoints.get(endpoint).incrementAndGet();
            return ir;
        });
        endpoints.put(endpoint, new AtomicInteger());
        return endpoint;
    }

    IMocksControl c;
    BundleContext bc;
    RemoteServiceAdmin rsa;
    TopologyManagerImport tm;
    Map<EndpointDescription, AtomicInteger> endpoints = new ConcurrentHashMap<>();

    @Before
    public void setUp() {
        c = createControl();
        c.makeThreadSafe(true);
        bc = mockBundleContext(c);
        rsa = c.createMock(RemoteServiceAdmin.class);
        tm = new TopologyManagerImport(bc);
    }

    public void start() {
        c.replay();
        tm.start();
    }

    @After
    public void tearDown() {
        tm.stop();
        c.verify();
    }

    private void assertImports(EndpointDescription endpoint, int registrations) throws InterruptedException {
        long end = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < end) {
            if (endpoints.get(endpoint).get() == registrations)
                return;
            Thread.sleep(10);
        }
        assertEquals("wrong number of open import registrations", registrations, endpoints.get(endpoint).get());
    }

    @Test
    public void testAddEndpointBeforeRsa() throws InterruptedException {
        EndpointDescription endpoint = createEndpoint();
        start();

        tm.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint), "myFilter");
        tm.add(rsa);
        assertImports(endpoint, 1);
    }

    @Test
    public void testAddEndpointAfterRsa() throws InterruptedException {
        EndpointDescription endpoint = createEndpoint();
        start();

        tm.add(rsa);
        tm.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint), "myFilter");
        assertImports(endpoint, 1);
    }

    @Test
    public void testAddEndpointTwice() throws InterruptedException {
        EndpointDescription endpoint = createEndpoint();
        start();

        tm.add(rsa);
        EndpointEvent event = new EndpointEvent(EndpointEvent.ADDED, endpoint);
        tm.endpointChanged(event, "myFilter");
        assertImports(endpoint, 1);
        tm.endpointChanged(event, "myFilter");
        assertImports(endpoint, 1); // still one one import
    }

    @Test
    public void testRemoveEndpoint() throws InterruptedException {
        EndpointDescription endpoint = createEndpoint();
        start();

        tm.add(rsa);
        tm.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint), "myFilter");
        assertImports(endpoint, 1);
        tm.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, endpoint), "myFilter");
        assertImports(endpoint, 0);
    }
}
