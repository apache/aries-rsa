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
package org.apache.aries.rsa.discovery.zookeeper;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointListener;
import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

@RunWith(MockitoJUnitRunner.class)
public class InterestManagerTest {
    
    @Mock
    private ZookeeperEndpointRepository repository;
    
    @Mock
    private EndpointEventListener epListener1;
    
    @Mock
    private EndpointEventListener epListener2;

    @Mock
    private ZookeeperEndpointListener listener;

    private InterestManager im;
    
    @Test
    public void testEndpointListenerTrackerCustomizer() {
        when(repository.createListener(Mockito.any())).thenReturn(listener);
        im = new InterestManager();
        im.bindARepository(repository);
        ServiceReference<EndpointEventListener> sref = createService("(objectClass=mine)");
        ServiceReference<EndpointEventListener> sref2 = createService("(objectClass=mine)");
        // sref has no scope -> nothing should happen
        assertNumInterests(0);

        im.bindEndpointEventListener(sref, epListener1);
        assertNumInterests(1);

        im.bindEndpointEventListener(sref, epListener1);
        assertNumInterests(1);

        im.bindEndpointEventListener(sref2, epListener2);
        assertNumInterests(2);

        im.unbindEndpointEventListener(sref);
        assertNumInterests(1);

        im.unbindEndpointEventListener(sref);
        assertNumInterests(1);

        im.unbindEndpointEventListener(sref2);
        assertNumInterests(0);
    }

    private void assertNumInterests(int expectedNum) {
        assertEquals(expectedNum, im.getInterests().size());
    }

    @SuppressWarnings("unchecked")
    private ServiceReference<EndpointEventListener> createService(String scope) {
        ServiceReference<EndpointEventListener> sref = Mockito.mock(ServiceReference.class);
        when(sref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE)).thenReturn(scope);
        return sref;
    }

}
