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
package org.apache.aries.rsa.topologymanager.exporter;

import static java.util.Arrays.asList;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import com.shazam.shazamcrest.MatcherAssert;
import com.shazam.shazamcrest.matcher.Matchers;

@SuppressWarnings({
    "rawtypes", "unchecked",
   })
public class EndpointListenerNotifierTest {
    
    @Before
    public void before() {
    }

    @Test
    public void testNotifyListener() throws InvalidSyntaxException {
        IMocksControl c = EasyMock.createControl();
        EndpointEventListener epl = c.createMock(EndpointEventListener.class);
        Capture<EndpointEvent> capturedEvents = newCapture(CaptureType.ALL);
        Capture<String> capturedFilters = newCapture(CaptureType.ALL);
        epl.endpointChanged(capture(capturedEvents), capture(capturedFilters));
        expectLastCall().anyTimes();
        
        EndpointDescription endpoint1 = createEndpoint("myClass");
        EndpointDescription endpoint2 = createEndpoint("notMyClass");

        c.replay();
        
        EndpointListenerNotifier notifier = new EndpointListenerNotifier();
        Filter filter = FrameworkUtil.createFilter("(objectClass=myClass)");
        notifier.add(epl, new HashSet(asList(filter)), Collections.<EndpointDescription>emptyList());
        notifier.sendEvent(new EndpointEvent(EndpointEvent.ADDED, endpoint1));
        notifier.sendEvent(new EndpointEvent(EndpointEvent.ADDED, endpoint2));
        notifier.sendEvent(new EndpointEvent(EndpointEvent.REMOVED, endpoint1));
        notifier.sendEvent(new EndpointEvent(EndpointEvent.REMOVED, endpoint2));
        c.verify();

        // Expect listener to be called for endpoint1 but not for endpoint2 
        List<EndpointEvent> expected = Arrays.asList(
                new EndpointEvent(EndpointEvent.ADDED, endpoint1),
                new EndpointEvent(EndpointEvent.REMOVED, endpoint1)
                );
                
        MatcherAssert.assertThat(capturedEvents.getValues(), Matchers.sameBeanAs(expected));
    }

    private EndpointDescription createEndpoint(String iface) {
        Map<String, Object> props = new Hashtable<>();
        props.put("objectClass", new String[]{iface});
        props.put(RemoteConstants.ENDPOINT_ID, iface);
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "any");
        return new EndpointDescription(props);
    }

    @Test
    public void testNormalizeScopeForSingleString() {
        ServiceReference sr = createListenerServiceWithFilter("(myProp=A)");
        Set<Filter> res = EndpointListenerNotifier.filtersFromEL(sr);
        assertEquals(1, res.size());
        Filter filter = res.iterator().next();
        filterMatches(filter);
    }

    @Test
    public void testNormalizeScopeForStringArray() {
        String[] filters = {"(myProp=A)", "(otherProp=B)"};
        ServiceReference sr = createListenerServiceWithFilter(filters); 
        Set<Filter> res = EndpointListenerNotifier.filtersFromEL(sr);
        assertEquals(filters.length, res.size());
        Iterator<Filter> it = res.iterator();
        Filter filter1 = it.next();
        Filter filter2 = it.next();
        Dictionary<String, String> props = new Hashtable();
        props.put("myProp", "A");
        assertThat(filter1.match(props) || filter2.match(props), is(true));
    }

    @Test
    public void testNormalizeScopeForCollection() {
        Collection<String> collection = Arrays.asList("(myProp=A)", "(otherProp=B)");
        ServiceReference sr = createListenerServiceWithFilter(collection);
        Set<Filter> res = EndpointListenerNotifier.filtersFromEL(sr);
        Iterator<Filter> it = res.iterator();
        Filter filter1 = it.next();
        Filter filter2 = it.next();
        Dictionary<String, String> props = new Hashtable();
        props.put("myProp", "A");
        Assert.assertThat(filter1.match(props) || filter2.match(props), is(true));
    }
    
    private void filterMatches(Filter filter) {
        Dictionary<String, String> props = new Hashtable();
        props.put("myProp", "A");
        Assert.assertTrue("Filter should match", filter.match(props));
    }

    private ServiceReference createListenerServiceWithFilter(Object filters) {
        ServiceReference sr = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sr.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE)).andReturn(filters);
        EasyMock.replay(sr);
        return sr;
    }
    
}
