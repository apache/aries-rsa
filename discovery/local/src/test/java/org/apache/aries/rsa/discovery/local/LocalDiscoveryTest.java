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
package org.apache.aries.rsa.discovery.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

public class LocalDiscoveryTest {

    @Test
    public void testPreExistingBundles() throws Exception {
        Bundle b1 = EasyMock.createMock(Bundle.class);
        EasyMock.expect(b1.getState()).andReturn(Bundle.RESOLVED);
        EasyMock.replay(b1);
        Bundle b2 = EasyMock.createMock(Bundle.class);
        EasyMock.expect(b2.getState()).andReturn(Bundle.ACTIVE);
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put("Remote-Service", "OSGI-INF/remote-service/");
        EasyMock.expect(b2.getHeaders()).andReturn(headers);

        URL rs3URL = getClass().getResource("/ed3.xml");
        URL rs4URL = getClass().getResource("/ed4.xml");
        List<URL> urls = Arrays.asList(rs3URL, rs4URL);
        EasyMock.expect(b2.findEntries("OSGI-INF/remote-service", "*.xml", false))
            .andReturn(Collections.enumeration(urls));
        EasyMock.replay(b2);

        Bundle[] bundles = new Bundle[] {b1, b2};

        LocalDiscovery ld = new LocalDiscovery();
        ld.processExistingBundles(bundles);

        assertEquals(3, ld.endpointDescriptions.size());
        Set<String> expected = new HashSet<>(
            Arrays.asList("http://somewhere:12345", "http://somewhere:1", "http://somewhere"));
        Set<String> actual = new HashSet<>();
        for (Map.Entry<EndpointDescription, Bundle> entry : ld.endpointDescriptions.entrySet()) {
            assertSame(b2, entry.getValue());
            actual.add(entry.getKey().getId());
        }
        assertEquals(expected, actual);
    }

    @Test
    public void testBundleChanged() throws Exception {
        LocalDiscovery ld = new LocalDiscovery();

        Bundle bundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(bundle.getSymbolicName()).andReturn("testing.bundle").anyTimes();
        EasyMock.expect(bundle.getState()).andReturn(Bundle.ACTIVE);
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put("Remote-Service", "OSGI-INF/rsa/");
        EasyMock.expect(bundle.getHeaders()).andReturn(headers);
        EasyMock.expect(bundle.findEntries("OSGI-INF/rsa", "*.xml", false))
            .andReturn(Collections.enumeration(
                Collections.singleton(getClass().getResource("/ed3.xml"))));
        EasyMock.replay(bundle);

        BundleEvent be0 = new BundleEvent(BundleEvent.INSTALLED, bundle);
        ld.bundleChanged(be0);
        assertEquals(0, ld.endpointDescriptions.size());

        ServiceReference<EndpointEventListener> sr = epListenerWithScope("(objectClass=*)");

        EndpointEventListener endpointListener = EasyMock.createMock(EndpointEventListener.class);
        endpointListener.endpointChanged(EasyMock.anyObject(EndpointEvent.class), EasyMock.eq("(objectClass=*)"));
        EasyMock.expectLastCall();
        EasyMock.replay(endpointListener);
        ld.bindListener(sr, endpointListener);

        // Start the bundle
        BundleEvent be = new BundleEvent(BundleEvent.STARTED, bundle);
        ld.bundleChanged(be);
        assertEquals(1, ld.endpointDescriptions.size());
        EndpointDescription endpoint = ld.endpointDescriptions.keySet().iterator().next();
        assertEquals("http://somewhere:12345", endpoint.getId());
        assertSame(bundle, ld.endpointDescriptions.get(endpoint));

        EasyMock.verify(endpointListener);

        // Stop the bundle
        EasyMock.reset(endpointListener);
        endpointListener.endpointChanged(EasyMock.anyObject(EndpointEvent.class), EasyMock.eq("(objectClass=*)"));
        EasyMock.expectLastCall();
        EasyMock.replay(endpointListener);

        BundleEvent be1 = new BundleEvent(BundleEvent.STOPPED, bundle);
        ld.bundleChanged(be1);
        assertEquals(0, ld.endpointDescriptions.size());

        EasyMock.verify(endpointListener);
    }

    @Test
    public void testEndpointListenerService() throws Exception {
        LocalDiscovery ld = new LocalDiscovery();

        Bundle bundle = createBundle();
        BundleEvent event = new BundleEvent(BundleEvent.STARTED, bundle);
        ld.bundleChanged(event);
        assertEquals(2, ld.endpointDescriptions.size());

        ServiceReference<EndpointEventListener> sr = epListenerWithScope("(objectClass=org.example.ClassA)");

        EndpointEventListener el = EasyMock.createMock(EndpointEventListener.class);
        el.endpointChanged(EasyMock.anyObject(EndpointEvent.class),
                EasyMock.eq("(objectClass=org.example.ClassA)"));
        EasyMock.expectLastCall();
        EasyMock.replay(el);

        // Add the EndpointListener Service
        assertEquals("Precondition failed", 0, ld.listenerToFilters.size());
        assertEquals("Precondition failed", 0, ld.filterToListeners.size());
        ld.bindListener(sr, el);

        assertEquals(1, ld.listenerToFilters.size());
        assertEquals(Collections.singletonList("(objectClass=org.example.ClassA)"), ld.listenerToFilters.get(el));
        assertEquals(1, ld.filterToListeners.size());
        assertEquals(Collections.singletonList(el), ld.filterToListeners.get("(objectClass=org.example.ClassA)"));

        EasyMock.verify(el);

        // Modify the EndpointListener Service
        // no need to reset the mock for this...
        ServiceReference<EndpointEventListener> sr2 = epListenerWithScope("(|(objectClass=org.example.ClassA)(objectClass=org.example.ClassB))");

        EasyMock.reset(el);
        final Set<String> actualEndpoints = new HashSet<>();
        el.endpointChanged(EasyMock.anyObject(EndpointEvent.class),
                EasyMock.eq("(|(objectClass=org.example.ClassA)(objectClass=org.example.ClassB))"));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                EndpointEvent event = (EndpointEvent) EasyMock.getCurrentArguments()[0];
                EndpointDescription endpoint = event.getEndpoint();
                actualEndpoints.addAll(endpoint.getInterfaces());
                return null;
            }
        }).times(2);
        EasyMock.replay(el);

        ld.unbindListener(el);
        ld.bindListener(sr2, el);
        assertEquals(1, ld.listenerToFilters.size());
        assertEquals(Arrays.asList("(|(objectClass=org.example.ClassA)(objectClass=org.example.ClassB))"),
            ld.listenerToFilters.get(el));
        assertEquals(1, ld.filterToListeners.size());
        assertEquals(Collections.singletonList(el),
            ld.filterToListeners.get("(|(objectClass=org.example.ClassA)(objectClass=org.example.ClassB))"));

        EasyMock.verify(el);
        Set<String> expectedEndpoints = new HashSet<>(Arrays.asList("org.example.ClassA", "org.example.ClassB"));
        assertEquals(expectedEndpoints, actualEndpoints);

        // Remove the EndpointListener Service
        ld.unbindListener(el);
        assertEquals(0, ld.listenerToFilters.size());
        assertEquals(0, ld.filterToListeners.size());
    }

    @Test
    public void testRegisterTracker() throws Exception {
        LocalDiscovery ld = new LocalDiscovery();

        final Map<String, Object> props = new Hashtable<>();
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, "(objectClass=Aaaa)");
        @SuppressWarnings("unchecked")
        ServiceReference<EndpointEventListener> sr = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sr.getPropertyKeys()).andReturn(props.keySet().toArray(new String[] {})).anyTimes();
        EasyMock.expect(sr.getProperty((String) EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                return props.get(EasyMock.getCurrentArguments()[0]);
            }
        }).anyTimes();
        EasyMock.replay(sr);

        EndpointEventListener endpointListener = EasyMock.createMock(EndpointEventListener.class);
        EasyMock.replay(endpointListener);

        assertEquals("Precondition failed", 0, ld.listenerToFilters.size());
        assertEquals("Precondition failed", 0, ld.filterToListeners.size());
        ld.bindListener(sr, endpointListener);

        assertEquals(1, ld.listenerToFilters.size());
        assertEquals(Collections.singletonList("(objectClass=Aaaa)"), ld.listenerToFilters.get(endpointListener));
        assertEquals(1, ld.filterToListeners.size());
        assertEquals(Collections.singletonList(endpointListener), ld.filterToListeners.get("(objectClass=Aaaa)"));

        // Add another one with the same scope filter
        @SuppressWarnings("unchecked")
        ServiceReference<EndpointEventListener> sr2 = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sr2.getPropertyKeys()).andReturn(props.keySet().toArray(new String[] {})).anyTimes();
        EasyMock.expect(sr2.getProperty((String) EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                return props.get(EasyMock.getCurrentArguments()[0]);
            }
        }).anyTimes();
        EasyMock.replay(sr2);

        EndpointEventListener endpointListener2 = EasyMock.createMock(EndpointEventListener.class);
        EasyMock.replay(endpointListener2);
        ld.bindListener(sr2, endpointListener2);

        assertEquals(2, ld.listenerToFilters.size());
        assertEquals(Collections.singletonList("(objectClass=Aaaa)"), ld.listenerToFilters.get(endpointListener));
        assertEquals(Collections.singletonList("(objectClass=Aaaa)"), ld.listenerToFilters.get(endpointListener2));

        assertEquals(1, ld.filterToListeners.size());
        List<EndpointEventListener> endpointListeners12 = Arrays.asList(endpointListener, endpointListener2);
        assertEquals(endpointListeners12, ld.filterToListeners.get("(objectClass=Aaaa)"));

        // Add another listener with a multi-value scope
        final Map<String, Object> props2 = new Hashtable<>();
        props2.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, Arrays.asList("(objectClass=X)", "(objectClass=Y)"));
        @SuppressWarnings("unchecked")
        ServiceReference<EndpointEventListener> sr3 = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sr3.getPropertyKeys()).andReturn(props2.keySet().toArray(new String[] {})).anyTimes();
        EasyMock.expect(sr3.getProperty((String) EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                return props2.get(EasyMock.getCurrentArguments()[0]);
            }
        }).anyTimes();
        EasyMock.replay(sr3);

        EndpointEventListener endpointListener3 = EasyMock.createMock(EndpointEventListener.class);
        EasyMock.replay(endpointListener3);
        ld.bindListener(sr3, endpointListener3);

        assertEquals(3, ld.listenerToFilters.size());
        assertEquals(Collections.singletonList("(objectClass=Aaaa)"), ld.listenerToFilters.get(endpointListener));
        assertEquals(Collections.singletonList("(objectClass=Aaaa)"), ld.listenerToFilters.get(endpointListener2));
        assertEquals(Arrays.asList("(objectClass=X)", "(objectClass=Y)"), ld.listenerToFilters.get(endpointListener3));

        assertEquals(3, ld.filterToListeners.size());
        assertEquals(endpointListeners12, ld.filterToListeners.get("(objectClass=Aaaa)"));
        assertEquals(Collections.singletonList(endpointListener3), ld.filterToListeners.get("(objectClass=X)"));
        assertEquals(Collections.singletonList(endpointListener3), ld.filterToListeners.get("(objectClass=Y)"));
    }

    @Test
    public void testClearTracker() throws Exception {
        LocalDiscovery ld = new LocalDiscovery();

        EndpointEventListener endpointListener = EasyMock.createMock(EndpointEventListener.class);
        ld.listenerToFilters.put(endpointListener,
            new ArrayList<>(Arrays.asList("(a=b)", "(objectClass=foo.bar.Bheuaark)")));
        ld.filterToListeners.put("(a=b)", new ArrayList<>(Arrays.asList(endpointListener)));
        ld.filterToListeners.put("(objectClass=foo.bar.Bheuaark)",
            new ArrayList<>(Arrays.asList(endpointListener)));

        assertEquals(1, ld.listenerToFilters.size());
        assertEquals(2, ld.filterToListeners.size());
        assertEquals(1, ld.filterToListeners.values().iterator().next().size());
        ld.unbindListener(EasyMock.createMock(EndpointEventListener.class));
        assertEquals(1, ld.listenerToFilters.size());
        assertEquals(2, ld.filterToListeners.size());
        assertEquals(1, ld.filterToListeners.values().iterator().next().size());
        ld.unbindListener(endpointListener);
        assertEquals(0, ld.listenerToFilters.size());
        assertEquals(0, ld.filterToListeners.size());
    }

    private ServiceReference<EndpointEventListener> epListenerWithScope(String scope) {
        final Map<String, Object> props = new Hashtable<>();
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, new String[] {scope});
        return mockService(props);
    }
    
    private ServiceReference<EndpointEventListener> mockService(final Map<String, Object> props) {
        @SuppressWarnings("unchecked")
        ServiceReference<EndpointEventListener> sr = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sr.getPropertyKeys()).andReturn(props.keySet().toArray(new String[] {})).anyTimes();
        EasyMock.expect(sr.getProperty((String) EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                return props.get(EasyMock.getCurrentArguments()[0]);
            }
        }).anyTimes();
    
        EasyMock.replay(sr);
        return sr;
    }

    private Bundle createBundle() {
        Bundle bundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(bundle.getState()).andReturn(Bundle.ACTIVE);
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put("Remote-Service", "OSGI-INF/rsa/ed4.xml");
        EasyMock.expect(bundle.getHeaders()).andReturn(headers);
        EasyMock.expect(bundle.findEntries("OSGI-INF/rsa", "ed4.xml", false))
            .andReturn(Collections.enumeration(
                Collections.singleton(getClass().getResource("/ed4.xml"))));
        EasyMock.replay(bundle);
        return bundle;
    }
}
