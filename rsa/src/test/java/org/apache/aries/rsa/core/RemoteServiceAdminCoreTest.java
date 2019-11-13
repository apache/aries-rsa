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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.aries.rsa.core.event.EventProducer;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

@SuppressWarnings({
    "rawtypes", "unchecked"
   })
public class RemoteServiceAdminCoreTest {

    private static final String MYCONFIG = "myconfig";
    private IMocksControl c;
    private BundleContext rsaContext;
    private RemoteServiceAdminCore rsaCore;
    private BundleContext apiContext;
    private DummyProvider provider;
    
    @Before
    public void setup() throws InvalidSyntaxException {
        c = EasyMock.createControl();      
        rsaContext = c.createMock(BundleContext.class);
        Bundle b = createDummyRsaBundle(rsaContext);
        expect(rsaContext.getProperty(Constants.FRAMEWORK_VERSION)).andReturn("1111").anyTimes();
        expect(rsaContext.getProperty(Constants.FRAMEWORK_UUID)).andReturn("some_uuid1").anyTimes();

        expect(rsaContext.getBundle()).andReturn(b).anyTimes();
        apiContext = c.createMock(BundleContext.class);
        provider = new DummyProvider();
        PackageUtil packageUtil = new PackageUtil(rsaContext) {
            @Override
            public String getVersion(Class<?> iClass) {
                return "1.0.0";
            }
        };
        EventProducer eventProducer = new EventProducer(rsaContext) {
            protected void notifyListeners(org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent rsae) {
                // skip
            }
        };
        rsaCore = new RemoteServiceAdminCore(rsaContext, apiContext, eventProducer, provider, packageUtil) {
            protected void createServiceListener() {}
        };
    }

    @Test
    public void testDontExportOwnServiceProxies() throws InvalidSyntaxException {
        Map<String, Object> sProps = new HashMap<>();
        sProps.put("objectClass", new String[] {"a.b.C"});
        sProps.put(RemoteConstants.SERVICE_IMPORTED, true);
        sProps.put("service.exported.interfaces", "*");
        ServiceReference sref = mockServiceReference(sProps);

        c.replay();

        List<ExportRegistration> exRefs = rsaCore.exportService(sref, null);

        assertNotNull(exRefs);
        assertEquals(0, exRefs.size());
        assertEquals(rsaCore.getExportedServices().size(), 0);

        c.verify();
    }

    @Test
    public void testDoNotImportUnsupportedConfig() {
        EndpointDescription endpoint = createEndpointDesc("unsupportedConfiguration");
        
        c.replay();

        assertNull(rsaCore.importService(endpoint));
        assertEquals(0, rsaCore.getImportedEndpoints().size());
        
        c.verify();
    }

    @Test
    public void testImport() {
        expect(apiContext.registerService(EasyMock.aryEq(new String[]{"es.schaaf.my.class"}), anyObject(), (Dictionary<String, ? >)anyObject())).andReturn(null);

        c.replay();
        EndpointDescription endpoint2 = createEndpointDesc(MYCONFIG);

        ImportRegistration ireg = rsaCore.importService(endpoint2);
        assertNotNull(ireg);

        assertEquals(1, rsaCore.getImportedEndpoints().size());

        // lets import the same endpoint once more -> should get a copy of the ImportRegistration
        ImportRegistration ireg2 = rsaCore.importService(endpoint2);
        assertNotNull(ireg2);
        assertEquals(2, rsaCore.getImportedEndpoints().size());

        assertEquals(ireg.getImportReference(), (rsaCore.getImportedEndpoints().toArray())[0]);

        assertEquals(ireg.getImportReference().getImportedEndpoint(), ireg2.getImportReference()
            .getImportedEndpoint());

        // remove the registration

        // first call shouldn't remove the import
        ireg2.close();
        assertEquals(1, rsaCore.getImportedEndpoints().size());

        // second call should really close and remove the import
        ireg.close();
        assertEquals(0, rsaCore.getImportedEndpoints().size());

        c.verify();
    }

    @Test
    public void testImportWithMultipleInterfaces() {
        expect(apiContext.registerService(EasyMock.aryEq(new String[]{"es.schaaf.my.class", "java.lang.Runnable"}), anyObject(), (Dictionary<String, ? >)anyObject())).andReturn(null);

        c.replay();

        Map<String, Object> p = new HashMap<>();
        p.put(RemoteConstants.ENDPOINT_ID, "http://google.de");
        p.put(Constants.OBJECTCLASS, new String[] {
            "es.schaaf.my.class",
            "java.lang.Runnable"
        });
        p.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, MYCONFIG);
        EndpointDescription endpoint = new EndpointDescription(p);

        ImportRegistration ireg = rsaCore.importService(endpoint);

        assertNotNull(ireg);
        assertEquals(1, rsaCore.getImportedEndpoints().size());

        // lets import the same endpoint once more -> should get a copy of the ImportRegistration
        ImportRegistration ireg2 = rsaCore.importService(endpoint);
        assertNotNull(ireg2);
        assertEquals(2, rsaCore.getImportedEndpoints().size());

        assertEquals(ireg.getImportReference(), (rsaCore.getImportedEndpoints().toArray())[0]);

        assertEquals(ireg.getImportReference().getImportedEndpoint(), ireg2.getImportReference()
            .getImportedEndpoint());

        EndpointDescription importedEndpoint = ireg.getImportReference().getImportedEndpoint();
        assertEquals(2, importedEndpoint.getInterfaces().size());

        c.verify();
    }

    @Test
    public void testExport() throws Exception {
        final Map<String, Object> sProps = new HashMap<>();
        sProps.put("objectClass", new String[] {"java.lang.Runnable"});
        sProps.put("service.id", 51L);
        sProps.put("myProp", "myVal");
        sProps.put("service.exported.interfaces", "*");
        ServiceReference sref = mockServiceReference(sProps);

        provider.endpoint = createEndpoint(sProps);
        ServiceReference sref2 = mockServiceReference(sProps);
        c.replay();

        // Export the service for the first time
        List<ExportRegistration> eregs = rsaCore.exportService(sref, null);
        assertEquals(1, eregs.size());
        ExportRegistration ereg = eregs.iterator().next();
        assertNull(ereg.getException());
        assertSame(sref, ereg.getExportReference().getExportedService());
        EndpointDescription endpoint = ereg.getExportReference().getExportedEndpoint();

        Map<String, Object> edProps = endpoint.getProperties();
        assertEquals("http://something", edProps.get("endpoint.id"));
        assertNotNull(edProps.get("service.imported"));
        assertThat((String[]) edProps.get("objectClass"), array(equalTo("java.lang.Runnable")));
        assertThat((String[]) edProps.get("service.imported.configs"), array(equalTo(MYCONFIG)));

        // Ask to export the same service again, this should not go through the whole process again but simply return
        // a copy of the first instance.
        List<ExportRegistration> eregs2 = rsaCore.exportService(sref2, null);
        assertEquals(1, eregs2.size());
        ExportRegistration ereg2 = eregs2.iterator().next();
        assertNull(ereg2.getException());
        assertEquals(ereg.getExportReference().getExportedEndpoint().getProperties(),
                ereg2.getExportReference().getExportedEndpoint().getProperties());

        assertNumExports(2);

        ereg.close();
        assertNumExports(1);
        
        ereg2.close();
        assertNumExports(0);
    }

    private void assertNumExports(int expectedNum) {
        assertThat("Number of export references", rsaCore.getExportedServices().size(), equalTo(expectedNum));
    }

    @Test
    public void testExportWrongConfig() throws Exception {
        final Map<String, Object> sProps = new HashMap<>();
        sProps.put("objectClass", new String[] {"java.lang.Runnable"});
        sProps.put("service.id", 51L);
        sProps.put("myProp", "myVal");
        sProps.put("service.exported.interfaces", "*");
        sProps.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "org.apache.cxf.ws");
        ServiceReference sref = mockServiceReference(sProps);

        c.replay();
        List<ExportRegistration> ereg = rsaCore.exportService(sref, null);

        // Service should not be exported as the exported config does not match
        assertEquals(0, ereg.size());
        c.verify();
    }

    @Test
    public void testExportOneConfigSupported() throws Exception {
        final Map<String, Object> sProps = new HashMap<>();
        sProps.put("objectClass", new String[] {"java.lang.Runnable"});
        sProps.put("service.id", 51L);
        sProps.put("myProp", "myVal");
        sProps.put("service.exported.interfaces", "*");
        sProps.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, new String[]{MYCONFIG, "aconfig"});
        ServiceReference sref = mockServiceReference(sProps);
        provider.endpoint = createEndpoint(sProps);
        c.replay();

        List<ExportRegistration> ereg = rsaCore.exportService(sref, null);
        assertEquals(1, ereg.size());
        ExportRegistration first = ereg.iterator().next();
        EndpointDescription exportedEndpoint = first.getExportReference().getExportedEndpoint();
        assertThat(exportedEndpoint.getConfigurationTypes(), contains(MYCONFIG));
    }

    @Test
    public void testExportException() throws Exception {
        final Map<String, Object> sProps = new HashMap<>();
        sProps.put("objectClass", new String[] {"java.lang.Runnable"});
        sProps.put("service.id", 51L);
        sProps.put("service.exported.interfaces", "*");
        ServiceReference sref = mockServiceReference(sProps);

        c.replay();
        provider.ex = new TestException();

        List<ExportRegistration> ereg = rsaCore.exportService(sref, sProps);
        assertEquals(1, ereg.size());
        assertTrue(ereg.get(0).getException() instanceof TestException);

        Collection<ExportReference> exportedServices = rsaCore.getExportedServices();
        assertEquals("No service was exported", 0, exportedServices.size());
        c.verify();
    }

    @Test
    public void testCreateEndpointProps() {
        c.replay();
        Map<String, Object> sd = new HashMap<>();
        sd.put(org.osgi.framework.Constants.SERVICE_ID, 42);
        Map<String, Object> props = rsaCore.createEndpointProps(sd, new Class[]{String.class});

        Assert.assertFalse(props.containsKey(org.osgi.framework.Constants.SERVICE_ID));
        assertEquals(42, props.get(RemoteConstants.ENDPOINT_SERVICE_ID));
        assertEquals("some_uuid1", props.get(RemoteConstants.ENDPOINT_FRAMEWORK_UUID));
        assertEquals(Arrays.asList("java.lang.String"),
                     Arrays.asList((Object[]) props.get(org.osgi.framework.Constants.OBJECTCLASS)));
        assertEquals("1.0.0", props.get("endpoint.package.version.java.lang"));
        c.verify();
    }

    private Endpoint createEndpoint(final Map<String, Object> sProps) throws IOException {
        Map<String, Object> eProps = new HashMap<>(sProps);
        eProps.put("endpoint.id", "http://something");
        eProps.put("service.imported.configs", new String[] {MYCONFIG});
        final EndpointDescription epd = new EndpointDescription(eProps);
        Endpoint er = c.createMock(Endpoint.class);
        expect(er.description()).andReturn(epd).anyTimes();
        er.close();
        expectLastCall();
        return er;
    }

    private Bundle createDummyRsaBundle(BundleContext bc) {
        Bundle b = c.createMock(Bundle.class);
        expect(b.getBundleContext()).andReturn(bc).anyTimes();
        expect(b.getSymbolicName()).andReturn("rsabundle").anyTimes();
        expect(b.getBundleId()).andReturn(10L).anyTimes();
        expect(b.getVersion()).andReturn(new Version("1.0.0")).anyTimes();
        expect(b.getHeaders()).andReturn(new Hashtable<String, String>()).anyTimes();
        return b;
    }

    private ServiceReference mockServiceReference(final Map<String, Object> sProps) {
        BundleContext bc = c.createMock(BundleContext.class);
        Bundle sb = c.createMock(Bundle.class);
        expect(sb.getBundleContext()).andReturn(bc).anyTimes();
        expect(bc.getBundle()).andReturn(sb).anyTimes();
    
        String[] propKeys = sProps.keySet().toArray(new String[] {});
        ServiceReference sref = c.createMock(ServiceReference.class);
        expect(sref.getBundle()).andReturn(sb).anyTimes();
        expect(sref.getPropertyKeys()).andReturn(propKeys).anyTimes();
        expect(sref.getProperty((String) EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                return sProps.get(EasyMock.getCurrentArguments()[0]);
            }
        }).anyTimes();
        Runnable svcObject = c.createMock(Runnable.class);
        expect(bc.getService(sref)).andReturn(svcObject).anyTimes();
        return sref;
    }
    
    private EndpointDescription createEndpointDesc(String configType) {
        Map<String, Object> p = new HashMap<>();
        p.put(RemoteConstants.ENDPOINT_ID, "http://google.de");
        p.put(Constants.OBJECTCLASS, new String[] {
            "es.schaaf.my.class"
        });
        p.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, configType);
        return new EndpointDescription(p);
    }

    @SuppressWarnings("serial")
    private static class TestException extends RuntimeException {
    }

    class DummyProvider implements DistributionProvider {
        
        Endpoint endpoint;
        RuntimeException ex;

        @Override
        public String[] getSupportedTypes() {
            return new String[]{MYCONFIG};
        }

        @Override
        public Endpoint exportService(Object serviceO, BundleContext serviceContext,
                Map<String, Object> effectiveProperties, Class[] exportedInterfaces) {
            if (ex != null) {
                throw ex;
            }
            return endpoint;
        }

        @Override
        public Object importEndpoint(ClassLoader cl, BundleContext consumerContext, Class[] interfaces,
                EndpointDescription endpoint) {
            return null;
        }
    }
}
