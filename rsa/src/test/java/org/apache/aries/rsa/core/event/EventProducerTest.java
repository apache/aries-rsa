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
package org.apache.aries.rsa.core.event;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import org.apache.aries.rsa.core.CloseHandler;
import org.apache.aries.rsa.core.ExportRegistrationImpl;
import org.apache.aries.rsa.spi.Endpoint;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;

@SuppressWarnings({"rawtypes", "unchecked"})
public class EventProducerTest {
    
    private IMocksControl c;
    private Capture<RemoteServiceAdminEvent> capturedEvent;
    private Bundle bundle;
    private BundleContext bc;
    private CloseHandler closeHandler;

    @Before
    public void before() throws InvalidSyntaxException {
        c = EasyMock.createNiceControl();
        capturedEvent = EasyMock.newCapture();
        bundle = createBundle();
        bc = bundleContextWithRsal(bundle);
        closeHandler = c.createMock(CloseHandler.class);
    }
    
    @Test
    public void testPublishNotification() throws Exception {
        final EndpointDescription epd = dummyEndpoint();
        Endpoint endpoint = c.createMock(Endpoint.class);
        expect(endpoint.description()).andReturn(epd);

        final ServiceReference sref = c.createMock(ServiceReference.class);

        c.replay();

        EventProducer eventProducer = new EventProducer(bc);
        ExportRegistrationImpl ereg = new ExportRegistrationImpl(sref, endpoint, closeHandler, eventProducer);
        eventProducer.publishNotification(ereg);

        RemoteServiceAdminEvent rsae = capturedEvent.getValue();
        Assert.assertNull(rsae.getException());
        Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_REGISTRATION, rsae.getType());
        Assert.assertSame(bundle, rsae.getSource());
        ExportReference er = rsae.getExportReference();
        Assert.assertSame(epd, er.getExportedEndpoint());
        Assert.assertSame(sref, er.getExportedService());

        c.verify();
    }

    @Test
    public void testPublishErrorNotification() throws Exception {
        c.replay();

        EventProducer eventProducer = new EventProducer(bc);
        final Exception exportException = new Exception();
        ExportRegistrationImpl ereg = new ExportRegistrationImpl(exportException, closeHandler, eventProducer);
        eventProducer.publishNotification(Arrays.<ExportRegistration>asList(ereg));

        RemoteServiceAdminEvent rsae = capturedEvent.getValue();
        Assert.assertSame(exportException, rsae.getException());
        Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_ERROR, rsae.getType());
        Assert.assertSame(bundle, rsae.getSource());
        Assert.assertNull(rsae.getImportReference());
        Assert.assertNull(rsae.getExportReference());

        c.verify();
    }

    private Bundle createBundle() {
        final Bundle bundle = c.createMock(Bundle.class);
        expect(bundle.getBundleId()).andReturn(42L).anyTimes();
        expect(bundle.getSymbolicName()).andReturn("test.bundle").anyTimes();
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put("Bundle-Version", "1.2.3.test");
        expect(bundle.getHeaders()).andReturn(headers).anyTimes();
        return bundle;
    }

    private BundleContext bundleContextWithRsal(Bundle bundle)
            throws InvalidSyntaxException {
        
        RemoteServiceAdminListener rsal = c.createMock(RemoteServiceAdminListener.class);
        rsal.remoteAdminEvent(EasyMock.capture(capturedEvent));
        expectLastCall().atLeastOnce();
        
        ServiceReference rsalSref = c.createMock(ServiceReference.class);
        expect(rsalSref.getBundle()).andReturn(bundle).anyTimes();

        BundleContext bc = c.createMock(BundleContext.class);

        expect(bc.getBundle()).andReturn(bundle).anyTimes();
        expect(bc.getServiceReferences(RemoteServiceAdminListener.class.getName(), null))
                .andReturn(new ServiceReference[] {rsalSref}).anyTimes();
        expect(bc.getService(rsalSref)).andReturn(rsal).anyTimes();
        return bc;
    }

    private EndpointDescription dummyEndpoint() {
        final String uuid = UUID.randomUUID().toString();
        Map<String, Object> props = new HashMap<>();
        props.put(RemoteConstants.ENDPOINT_SERVICE_ID, Long.MAX_VALUE);
        props.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, uuid);
        props.put(RemoteConstants.ENDPOINT_ID, "foo://bar");
        props.put(Constants.OBJECTCLASS, new String[] {"org.foo.Bar", "org.boo.Far"});
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, new String[] {"org.apache.cxf.ws"});
        final EndpointDescription epd = new EndpointDescription(props);
        return epd;
    }
}
