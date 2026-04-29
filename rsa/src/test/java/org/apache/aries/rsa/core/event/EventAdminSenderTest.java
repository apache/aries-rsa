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

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;

import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class EventAdminSenderTest {

    @Test
    public void testPublishNotification() throws Exception {
        final EndpointDescription epd = EasyMock.createNiceMock(EndpointDescription.class);
        EasyMock.expect(epd.getServiceId()).andReturn(Long.MAX_VALUE).anyTimes();
        final String uuid = UUID.randomUUID().toString();
        EasyMock.expect(epd.getFrameworkUUID()).andReturn(uuid).anyTimes();
        EasyMock.expect(epd.getId()).andReturn("foo://bar").anyTimes();
        final List<String> interfaces = Arrays.asList("org.foo.Bar", "org.boo.Far");
        EasyMock.expect(epd.getInterfaces()).andReturn(interfaces).anyTimes();
        EasyMock.expect(epd.getConfigurationTypes()).andReturn(Arrays.asList("org.apache.cxf.ws")).anyTimes();
        EasyMock.replay(epd);
        final ServiceReference sref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(sref);
        final ExportReference er = EasyMock.createNiceMock(ExportReference.class);
        EasyMock.expect(er.getExportedEndpoint()).andReturn(epd).anyTimes();
        EasyMock.expect(er.getExportedService()).andReturn(sref).anyTimes();
        EasyMock.replay(er);

        final Bundle bundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(bundle.getBundleId()).andReturn(42L).anyTimes();
        EasyMock.expect(bundle.getSymbolicName()).andReturn("test.bundle").anyTimes();
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put("Bundle-Version", "1.2.3.test");
        EasyMock.expect(bundle.getHeaders()).andReturn(headers).anyTimes();
        EasyMock.replay(bundle);

        EventAdmin ea = EasyMock.createNiceMock(EventAdmin.class);
        ea.postEvent(EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() {
                Event event = (Event) EasyMock.getCurrentArguments()[0];

                Assert.assertEquals("org/osgi/service/remoteserviceadmin/EXPORT_REGISTRATION", event.getTopic());
                Assert.assertSame(bundle, event.getProperty("bundle"));
                Assert.assertEquals(42L, event.getProperty("bundle.id"));
                Assert.assertEquals("test.bundle", event.getProperty("bundle.symbolicname"));
                Assert.assertEquals(new Version(1, 2, 3, "test"), event.getProperty("bundle.version"));
                Assert.assertNull(event.getProperty("exception"));
                Assert.assertNull(event.getProperty("exception.message"));
                Assert.assertNull(event.getProperty("exception.class"));

                Assert.assertEquals(Long.MAX_VALUE, event.getProperty("endpoint.service.id"));
                Assert.assertEquals(uuid, event.getProperty("endpoint.framework.uuid"));
                Assert.assertEquals("foo://bar", event.getProperty("endpoint.id"));
                Assert.assertArrayEquals(interfaces.toArray(new String[]{}), (String[]) event.getProperty("objectClass"));
                Assert.assertEquals(epd.getConfigurationTypes(), event.getProperty("service.imported.configs"));

                Assert.assertNotNull(event.getProperty("timestamp"));
                Assert.assertTrue((Long)event.getProperty("timestamp") <= System.currentTimeMillis());
                Assert.assertTrue((Long)event.getProperty("timestamp") > System.currentTimeMillis() - 30000);

                RemoteServiceAdminEvent rsae = (RemoteServiceAdminEvent) event.getProperty("event");
                Assert.assertNull(rsae.getException());
                Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_REGISTRATION, rsae.getType());
                Assert.assertSame(bundle, rsae.getSource());
                ExportReference er = rsae.getExportReference();
                Assert.assertSame(epd, er.getExportedEndpoint());
                Assert.assertSame(sref, er.getExportedService());

                return null;
            }
        });
        EasyMock.replay(ea);

        ServiceReference eaSref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(eaSref);

        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
        EasyMock.expect(bc.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.expect(bc.getServiceReference(EventAdmin.class)).andReturn(eaSref).anyTimes();
        EasyMock.expect(bc.getService(eaSref)).andReturn(ea).anyTimes();
        EasyMock.replay(bc);

        RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(
                RemoteServiceAdminEvent.EXPORT_REGISTRATION,
                bundle,
                er,
                null
        );
        new EventAdminSender(bc).send(event);
        EasyMock.verify(epd, sref, er, ea, eaSref, bc);
    }

    @Test
    public void testPublishErrorNotification() throws Exception {
        final EndpointDescription epd = EasyMock.createNiceMock(EndpointDescription.class);
        EasyMock.expect(epd.getInterfaces()).andReturn(Arrays.asList("org.foo.Bar")).anyTimes();
        EasyMock.replay(epd);
        final ServiceReference sref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(sref);
        final ExportReference er = EasyMock.createNiceMock(ExportReference.class);
        EasyMock.expect(er.getExportedEndpoint()).andReturn(epd).anyTimes();
        EasyMock.expect(er.getExportedService()).andReturn(sref).anyTimes();
        EasyMock.replay(er);

        final Bundle bundle = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(bundle.getBundleId()).andReturn(42L).anyTimes();
        EasyMock.expect(bundle.getSymbolicName()).andReturn("test.bundle").anyTimes();
        EasyMock.expect(bundle.getHeaders()).andReturn(new Hashtable<>()).anyTimes();
        EasyMock.replay(bundle);

        final Exception exportException = new Exception();

        EventAdmin ea = EasyMock.createNiceMock(EventAdmin.class);
        ea.postEvent(EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() {
                Event event = (Event) EasyMock.getCurrentArguments()[0];

                Assert.assertEquals("org/osgi/service/remoteserviceadmin/EXPORT_ERROR", event.getTopic());
                Assert.assertSame(bundle, event.getProperty("bundle"));
                Assert.assertEquals(42L, event.getProperty("bundle.id"));
                Assert.assertEquals("test.bundle", event.getProperty("bundle.symbolicname"));
                Assert.assertEquals(new Version("0"), event.getProperty("bundle.version"));
                Assert.assertSame(exportException, event.getProperty("exception"));
                Assert.assertSame(exportException.getMessage(), event.getProperty("exception.message"));
                Assert.assertSame(exportException.getClass().getName(), event.getProperty("exception.class"));
                Assert.assertEquals(epd.getConfigurationTypes(), event.getProperty("service.imported.configs"));
                Assert.assertArrayEquals(new String[]{"org.foo.Bar"}, (String[]) event.getProperty("objectClass"));

                RemoteServiceAdminEvent rsae = (RemoteServiceAdminEvent) event.getProperty("event");
                Assert.assertSame(exportException, rsae.getException());
                Assert.assertEquals(RemoteServiceAdminEvent.EXPORT_ERROR, rsae.getType());
                Assert.assertSame(bundle, rsae.getSource());
                ExportReference er = rsae.getExportReference();
                Assert.assertSame(epd, er.getExportedEndpoint());
                Assert.assertSame(sref, er.getExportedService());

                return null;
            }
        });
        EasyMock.replay(ea);

        ServiceReference eaSref = EasyMock.createNiceMock(ServiceReference.class);
        EasyMock.replay(eaSref);

        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);

        EasyMock.expect(bc.getBundle()).andReturn(bundle).anyTimes();
        EasyMock.expect(bc.getServiceReference(EventAdmin.class)).andReturn(eaSref).anyTimes();
        EasyMock.expect(bc.getService(eaSref)).andReturn(ea).anyTimes();
        EasyMock.replay(bc);

        RemoteServiceAdminEvent event = new RemoteServiceAdminEvent(
                RemoteServiceAdminEvent.EXPORT_ERROR,
                bundle,
                er,
                exportException
        );
        new EventAdminSender(bc).send(event);
        EasyMock.verify(epd, sref, er, ea, eaSref, bc);
    }
}
