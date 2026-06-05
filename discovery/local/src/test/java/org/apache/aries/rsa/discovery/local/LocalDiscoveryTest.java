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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.aries.rsa.spi.discovery.InterestManager;
import org.easymock.EasyMock;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

public class LocalDiscoveryTest {

    private static class Listener implements EndpointEventListener {

        // the service reference that provides the scope property for this listener
        private ServiceReference<EndpointEventListener> sref;
        // all received events and their matched filter, in order received
        private final Map<EndpointEvent, String> events = Collections.synchronizedMap(new LinkedHashMap<>());

        public Listener(String... scopes) {
            setFilter(scopes);
        }

        public Listener() {
            setFilter("(objectClass=*)");
        }

        public ServiceReference<EndpointEventListener> getServiceReference() {
            return sref;
        }

        public void setFilter(String... scopes) {
            sref = mockListenerServiceReference(scopes);
        }

        public void clear() {
            events.clear();
        }

        @Override
        public void endpointChanged(EndpointEvent event, String filter) {
            events.put(event, filter);
        }

        public Map<EndpointDescription, String> getEndpointFilters() {
            // replay the event sequence and return the remaining endpoints
            Map<EndpointDescription, String> endpoints = new LinkedHashMap<>();
            for (Map.Entry<EndpointEvent, String> e : events.entrySet()) {
                if (e.getKey().getType() ==  EndpointEvent.ADDED) {
                    endpoints.put(e.getKey().getEndpoint(), e.getValue());
                } else if (e.getKey().getType() ==  EndpointEvent.REMOVED) {
                    endpoints.remove(e.getKey().getEndpoint());
                }
            }
            return endpoints;
        }

        public Collection<EndpointDescription> getEndpoints() {
            return getEndpointFilters().keySet();
        }

        public Collection<String> getFilters() {
            return new ArrayList<>(getEndpointFilters().values());
        }

        public <T> Collection<T> getEndpointData(Function<EndpointDescription, T> getter) {
            return getEndpoints().stream().map(getter).collect(Collectors.toSet());
        }
    }

    @Test
    public void testPreExistingBundles() {
        Bundle b1 = createMock(Bundle.class);
        expect(b1.getState()).andReturn(Bundle.RESOLVED);
        replay(b1);
        Bundle b2 = mockBundle("ed3.xml", "ed4.xml");
        Bundle[] bundles = {b1, b2};
        BundleContext context = mockBundleContext(bundles);

        LocalDiscovery ld = new LocalDiscovery();
        Listener listener = new Listener();
        InterestManager im = ld.interestManager;
        im.addListener(listener.getServiceReference(), listener);

        ld.activate(context); // should get all endpoints from existing bundles on activation

        Collection<String> ednpointIds = listener.getEndpointData(EndpointDescription::getId);
        assertEquals(3, ednpointIds.size());
        Set<String> expected = new HashSet<>(
            Arrays.asList("http://somewhere:12345", "http://somewhere:1", "http://somewhere"));
        assertEquals(expected, ednpointIds);
    }

    @Test
    public void testBundleChanged() {
        Bundle bundle = mockBundle("ed3.xml");

        LocalDiscovery ld = new LocalDiscovery();
        Listener listener = new Listener();
        InterestManager im = ld.interestManager;
        im.addListener(listener.getServiceReference(), listener);

        // install the bundle
        ld.bundleChanged(new BundleEvent(BundleEvent.INSTALLED, bundle));
        assertEquals("no endpoint when installed", 0, listener.getEndpoints().size());

        // start the bundle
        ld.bundleChanged(new BundleEvent(BundleEvent.STARTED, bundle));
        assertEquals(1, listener.getEndpoints().size());
        String endpointId = listener.getEndpointData(EndpointDescription::getId).iterator().next();
        assertEquals("one endpoint when started", "http://somewhere:12345", endpointId);

        // stop the bundle
        ld.bundleChanged(new BundleEvent(BundleEvent.STOPPED, bundle));
        assertEquals("no endpoint when stopped", 0, listener.getEndpoints().size());

        // start it again
        ld.bundleChanged(new BundleEvent(BundleEvent.STARTED, bundle));
        assertEquals("one endpoint when restarted", 1, listener.getEndpoints().size());
    }

    @Test
    public void testEndpointListenerLifecycle() {
        LocalDiscovery ld = new LocalDiscovery();
        Listener listener = new Listener();
        InterestManager im = ld.interestManager;
        im.addListener(listener.getServiceReference(), listener);

        Bundle bundle = mockBundle(); // created with two endpoint descriptions, ClassA and ClassB
        ld.bundleChanged(new BundleEvent(BundleEvent.STARTED, bundle));
        assertEquals(2, listener.getEndpoints().size());

        // remove listener
        im.removeListener(listener.getServiceReference());

        // add new listener with scope A
        listener = new Listener("(objectClass=org.example.ClassA)");
        im.addListener(listener.getServiceReference(), listener);

        Collection<String> filters = listener.getFilters();
        assertEquals(1, filters.size());
        assertEquals(Collections.singletonList("(objectClass=org.example.ClassA)"), filters);

        // unbind the listener, modify its scope to A+B, and bind again
        listener.setFilter("(|(objectClass=org.example.ClassA)(objectClass=org.example.ClassB))");
        im.removeListener(listener.getServiceReference());
        listener.clear();
        im.addListener(listener.getServiceReference(), listener);

        Set<String> expectedInterfaces = new HashSet<>(Arrays.asList("org.example.ClassA", "org.example.ClassB"));
        Set<String> interfaces = listener.getEndpointData(EndpointDescription::getInterfaces).stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
        assertEquals(expectedInterfaces, interfaces);

        // now change the scope to C via updated listener service properties
        listener.setFilter("(objectClass=org.example.ClassC)");
        listener.clear();
        im.updateListener(listener.getServiceReference(), listener);

        assertEquals(0, listener.getEndpoints().size());

        // and update again to scope B
        listener.setFilter("(objectClass=org.example.ClassB)");
        listener.clear();
        im.updateListener(listener.getServiceReference(), listener);

        assertEquals(1, listener.getEndpoints().size());

        // remove the EndpointListener service
        im.removeListener(listener.getServiceReference());
    }

    @Test
    public void testRegisterListener() {
        LocalDiscovery ld = new LocalDiscovery();
        Listener listener = new Listener("(objectClass=org.example.ClassA)");
        InterestManager im = ld.interestManager;
        im.addListener(listener.getServiceReference(), listener);

        assertEquals("Precondition failed", 0, listener.getEndpoints().size());

        im.addListener(listener.getServiceReference(), listener);

        // add another one with the same scope filter
        Listener listener2 = new Listener("(objectClass=org.example.ClassA)");
        im.addListener(listener2.getServiceReference(), listener2);

        // add another listener with a multi-value scope
        Listener listener3 = new Listener("(objectClass=org.example.ClassA)", "(objectClass=org.example.ClassB)");
        im.addListener(listener3.getServiceReference(), listener3);

        Bundle bundle = mockBundle();
        ld.bundleChanged(new BundleEvent(BundleEvent.STARTED, bundle));

        // check that every listener got what it wants
        assertEquals(1, listener.getEndpoints().size());
        assertEquals(1, listener2.getEndpoints().size());
        assertEquals(2, listener3.getEndpoints().size());
    }

    @Test
    public void testUnregisterListener() {
        LocalDiscovery ld = new LocalDiscovery();
        InterestManager im = ld.interestManager;
        // start with two listeners
        Listener listener = new Listener("(objectClass=org.example.ClassA)");
        im.addListener(listener.getServiceReference(), listener);
        Listener listener2 = new Listener("(objectClass=org.example.ClassA)");
        im.addListener(listener2.getServiceReference(), listener2);

        // remove first listener and start bundle
        im.removeListener(listener.getServiceReference());
        Bundle bundle = mockBundle();
        ld.bundleChanged(new BundleEvent(BundleEvent.STARTED, bundle));

        // only the second listener gets endpoint
        assertEquals(0, listener.getEndpoints().size());
        assertEquals(1, listener2.getEndpoints().size());

        // stop the bundle
        ld.bundleChanged(new BundleEvent(BundleEvent.STOPPED, bundle));

        // both listeners have no endpoints
        assertEquals(0, listener.getEndpoints().size());
        assertEquals(0, listener2.getEndpoints().size());

        // remove the second bundle and start the bundle
        im.removeListener(listener2.getServiceReference());
        bundle = mockBundle();
        ld.bundleChanged(new BundleEvent(BundleEvent.STARTED, bundle));

        // both listeners have no endpoints
        assertEquals(0, listener.getEndpoints().size());
        assertEquals(0, listener2.getEndpoints().size());
    }

    private static ServiceReference<EndpointEventListener> mockListenerServiceReference(String... scopes) {
        final Map<String, Object> props = new Hashtable<>();
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, scopes);
        return mockServiceReference(props);
    }

    private static ServiceReference<EndpointEventListener> mockServiceReference(final Map<String, Object> props) {
        ServiceReference<EndpointEventListener> sr = createMock(ServiceReference.class);
        expect(sr.getPropertyKeys()).andReturn(props.keySet().toArray(new String[] {})).anyTimes();
        expect(sr.getProperty(anyObject()))
            .andAnswer(() -> props.get(EasyMock.getCurrentArguments()[0])).anyTimes();
        replay(sr);
        return sr;
    }

    private static Bundle mockBundle(String... resources) {
        Collection<URL> urls = Arrays.stream(resources)
            .map(r -> LocalDiscoveryTest.class.getResource("/" + r))
            .collect(Collectors.toList());
        Bundle bundle = createMock(Bundle.class);
        expect(bundle.getBundleId()).andReturn(Math.round(Math.random())).anyTimes();
        expect(bundle.getState()).andReturn(Bundle.ACTIVE).anyTimes();
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put("Remote-Service", "OSGI-INF/rsa/");
        expect(bundle.getHeaders()).andReturn(headers).anyTimes();
        expect(bundle.findEntries("OSGI-INF/rsa", "*.xml", false))
            .andAnswer(() -> Collections.enumeration(urls)).anyTimes();
        replay(bundle);
        return bundle;
    }

    private Bundle mockBundle() {
        return mockBundle("ed4.xml");
    }

    private static BundleContext mockBundleContext(Bundle[] bundles) {
        BundleContext context = createMock(BundleContext.class);
        expect(context.getBundles()).andReturn(bundles).anyTimes();
        context.addBundleListener(anyObject(BundleListener.class));
        context.removeBundleListener(anyObject(BundleListener.class));
        try {
            expect(context.createFilter(anyString()))
                .andAnswer(() -> FrameworkUtil.createFilter((String)getCurrentArguments()[0]));
            context.addServiceListener(anyObject(ServiceListener.class), anyString());
            expect(context.getServiceReferences(anyString(), anyString())).andReturn(null);
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
        replay(context);
        return context;
    }
}
