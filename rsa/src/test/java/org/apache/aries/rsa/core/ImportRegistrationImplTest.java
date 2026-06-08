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

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.remoteserviceadmin.EndpointDescription;

import static org.junit.Assert.*;

public class ImportRegistrationImplTest {

    @Test
    public void testException() {
        IMocksControl c = EasyMock.createNiceControl();
        Exception e = c.createMock(Exception.class);
        c.replay();

        ImportRegistrationImpl ireg = new ImportRegistrationImpl(null, null, null);
        ireg.init(e);

        assertEquals(e, ireg.getException());
        assertNull(ireg.getImportedEndpoint());
        assertNull(ireg.getImportedService());
    }

    @Test
    public void testDefaultCtor() {
        IMocksControl c = EasyMock.createNiceControl();
        EndpointDescription endpoint = c.createMock(EndpointDescription.class);
        CloseHandler closeHandler = c.createMock(CloseHandler.class);

        c.replay();

        ImportRegistrationImpl ireg = new ImportRegistrationImpl(endpoint, closeHandler, null);

        assertNull(ireg.getException());
        assertEquals(endpoint, ireg.getImportedEndpoint());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testCloneAndClose() {
        IMocksControl c = EasyMock.createControl();
        EndpointDescription endpoint = c.createMock(EndpointDescription.class);
        CloseHandler closeHandler = c.createMock(CloseHandler.class);

        ServiceRegistration sr = c.createMock(ServiceRegistration.class);
        ServiceReference sref = c.createMock(ServiceReference.class);
        EasyMock.expect(sr.getReference()).andReturn(sref).anyTimes();

        c.replay();

        ImportRegistrationImpl ireg1 = new ImportRegistrationImpl(endpoint, closeHandler, null);
        ireg1.init(null, sr);

        ImportRegistrationImpl ireg2 = new ImportRegistrationImpl(ireg1);

        ImportRegistrationImpl ireg3 = new ImportRegistrationImpl(ireg2);

        try {
            ireg2.init(null, sr);
            fail("An exception should be thrown here !");
        } catch (IllegalStateException e) {
            // must be thrown here
        }

        assertEquals(endpoint, ireg1.getImportedEndpoint());
        assertEquals(endpoint, ireg2.getImportedEndpoint());
        assertEquals(endpoint, ireg3.getImportedEndpoint());

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(ireg3));
        EasyMock.expectLastCall().once();

        c.replay();

        ireg3.close();
        ireg3.close(); // shouldn't change anything

        assertNull(ireg3.getImportedEndpoint());

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(ireg1));
        EasyMock.expectLastCall().once();

        c.replay();

        ireg1.close();

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(ireg2));
        EasyMock.expectLastCall().once();

        sr.unregister();
        EasyMock.expectLastCall().once();

        c.replay();

        ireg2.close();

        c.verify();
    }

    @Test
    public void testCloseAll() {
        IMocksControl c = EasyMock.createControl();
        EndpointDescription endpoint = c.createMock(EndpointDescription.class);
        CloseHandler closeHandler = c.createMock(CloseHandler.class);

        c.replay();

        ImportRegistrationImpl ireg1 = new ImportRegistrationImpl(endpoint, closeHandler, null);

        ImportRegistrationImpl ireg2 = new ImportRegistrationImpl(ireg1);

        ImportRegistrationImpl ireg3 = new ImportRegistrationImpl(ireg2);

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(ireg2));
        EasyMock.expectLastCall().once();

        c.replay();

        ireg2.close();

        c.verify();
        c.reset();

        closeHandler.onClose(EasyMock.eq(ireg1));
        EasyMock.expectLastCall().once();
        closeHandler.onClose(EasyMock.eq(ireg3));
        EasyMock.expectLastCall().once();

        c.replay();
        ireg3.closeAll();
        c.verify();
    }
}
