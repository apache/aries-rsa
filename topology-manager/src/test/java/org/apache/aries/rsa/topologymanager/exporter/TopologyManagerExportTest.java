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
package org.apache.aries.rsa.topologymanager.exporter;

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.aries.rsa.spi.ExportPolicy;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

import com.shazam.shazamcrest.MatcherAssert;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TopologyManagerExportTest {
    
    private IMocksControl c;
    private RemoteServiceAdmin rsa;
    private EndpointListenerNotifier notifier;
    private TopologyManagerExport exportManager;
    private Capture<EndpointEvent> events;

    @Before
    public void start() {
        c = EasyMock.createControl();
        rsa = c.createMock(RemoteServiceAdmin.class);

        notifier = c.createMock(EndpointListenerNotifier.class);
        events = EasyMock.newCapture(CaptureType.ALL);
        notifier.sendEvent(EasyMock.capture(events));
        EasyMock.expectLastCall().anyTimes();
        
        Executor executor = syncExecutor();
        ExportPolicy policy = new DefaultExportPolicy();
        exportManager = new TopologyManagerExport(notifier, executor, policy);
    }

    /**
     * This tests if the topology manager handles a service marked to be exported correctly by exporting it to
     * an available RemoteServiceAdmin and notifying an EndpointListener afterwards.
     *
     * @throws Exception
     */
    @Test
    public void testServiceExportUnexport() throws Exception {
        EndpointDescription epd = createEndpoint();
        ServiceReference sref = createUserService("*");
        expectServiceExported(sref, epd);
        EndpointDescription epd2 = createEndpoint();
        ServiceReference sref2 = createUserService("*");
        expectServiceExported(sref2, epd2);

        c.replay();
        exportManager.add(rsa);
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref2));
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED, sref));
        //exportManager.serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, sref));
        exportManager.remove(rsa);
        c.verify();

        List<EndpointEvent> expectedEvents = Arrays.asList(
                new EndpointEvent(EndpointEvent.ADDED, epd),
                new EndpointEvent(EndpointEvent.ADDED, epd2),
                new EndpointEvent(EndpointEvent.MODIFIED, epd),
                new EndpointEvent(EndpointEvent.REMOVED, epd),
                new EndpointEvent(EndpointEvent.REMOVED, epd2));
        MatcherAssert.assertThat(events.getValues(), sameBeanAs(expectedEvents));
    }

    @Test
    public void testExportExistingMultipleInterfaces() throws Exception {
        List<String> exportedInterfaces = Arrays.asList("a.b.C", "foo.Bar");
        final ServiceReference sref = createUserService(exportedInterfaces);
        expectServiceExported(sref, createEndpoint());

        c.replay();
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
        exportManager.add(rsa);
        c.verify();
    }

    @Test
    public void testExportExistingNoExportedInterfaces() throws Exception {
        String exportedInterfaces = "";
        final ServiceReference sref = createUserService(exportedInterfaces);
        c.replay();

        ExportPolicy policy = new DefaultExportPolicy();
        TopologyManagerExport exportManager = new TopologyManagerExport(notifier, syncExecutor(), policy);
        exportManager.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, sref));
        exportManager.add(rsa);
        c.verify();
    }

    private void expectServiceExported(
            final ServiceReference sref,
            EndpointDescription epd) {
        ExportRegistration exportRegistration = createExportRegistration(c, epd);
        expect(rsa.exportService(EasyMock.same(sref), (Map<String, Object>)EasyMock.anyObject()))
            .andReturn(Collections.singletonList(exportRegistration)).once();
    }

    private Executor syncExecutor() {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    private ExportRegistration createExportRegistration(IMocksControl c, EndpointDescription endpoint) {
        ExportRegistration exportRegistration = c.createMock(ExportRegistration.class);
        ExportReference exportReference = c.createMock(ExportReference.class);
        expect(exportRegistration.getExportReference()).andReturn(exportReference).anyTimes();
        expect(exportRegistration.getException()).andReturn(null).anyTimes();
        expect(exportReference.getExportedEndpoint()).andReturn(endpoint).anyTimes();
        exportRegistration.close();
        expectLastCall().anyTimes();
        expect(exportRegistration.update(EasyMock.anyObject(Map.class))).andReturn(endpoint).anyTimes();
        return exportRegistration;
    }

    private EndpointDescription createEndpoint() {
        Map<String, Object> props = new HashMap<>();
        props.put(RemoteConstants.ENDPOINT_ID, "1");
        props.put(Constants.OBJECTCLASS, new String[] {"abc"});
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "cxf");
        return new EndpointDescription(props);
    }

    private ServiceReference createUserService(Object exportedInterfaces) {
        final ServiceReference sref = c.createMock(ServiceReference.class);
        expect(sref.getProperty(EasyMock.same(RemoteConstants.SERVICE_EXPORTED_INTERFACES)))
            .andReturn(exportedInterfaces).anyTimes();
        Bundle srefBundle = c.createMock(Bundle.class);
        expect(sref.getBundle()).andReturn(srefBundle).anyTimes();
        expect(srefBundle.getSymbolicName()).andReturn("serviceBundleName").anyTimes();
        expect(sref.getProperty("objectClass")).andReturn("org.My").anyTimes();
        expect(sref.getPropertyKeys()).andReturn(new String[] {"objectClass"}).anyTimes();
        return sref;
    }
}
