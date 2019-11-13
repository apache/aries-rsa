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
package org.apache.aries.rsa.provider.tcp;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.provider.tcp.myservice.MyService;
import org.apache.aries.rsa.provider.tcp.myservice.MyServiceImpl;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.util.EndpointHelper;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class TcpProviderIntentTest {
    Class<?>[] exportedInterfaces;
    private BundleContext bc;
    private TCPProvider provider;
    private MyService myService;
    
    @Before
    public void before() {
        exportedInterfaces = new Class[] {MyService.class};
        bc = EasyMock.mock(BundleContext.class);
        provider = new TCPProvider();
        myService = new MyServiceImpl();
    }
    
    @Test
    public void basicAndAsnycIntents() {
        Map<String, Object> props = new HashMap<>();
        EndpointHelper.addObjectClass(props, exportedInterfaces);
        String[] standardIntents = new String[] {"osgi.basic", "osgi.async"};
        props.put(RemoteConstants.SERVICE_EXPORTED_INTENTS, standardIntents);
        Endpoint ep = provider.exportService(myService, bc, props, exportedInterfaces);
        Assert.assertThat("Service should be exported as the intents: " + Arrays.toString(standardIntents) + " must be supported", ep, notNullValue());
    }
    
    @Test
    public void unknownIntent() {
        Map<String, Object> props = new HashMap<>();
        EndpointHelper.addObjectClass(props, exportedInterfaces);
        props.put(RemoteConstants.SERVICE_EXPORTED_INTENTS, "unknown");
        Endpoint ep = provider.exportService(myService, bc, props, exportedInterfaces);
        Assert.assertThat("Service should not be exported as intent is not supported", ep, nullValue());
    }
    
    @Test
    public void unknownIntentExtra() {
        Map<String, Object> props = new HashMap<>();
        EndpointHelper.addObjectClass(props, exportedInterfaces);
        props.put(RemoteConstants.SERVICE_EXPORTED_INTENTS_EXTRA, "unknown");
        Endpoint ep = provider.exportService(myService, bc, props, exportedInterfaces);
        Assert.assertThat("Service should not be exported as intent is not supported", ep, nullValue());
    }


}
