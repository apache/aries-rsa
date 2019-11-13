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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.provider.tcp.myservice.MyService;
import org.apache.aries.rsa.provider.tcp.myservice.MyServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

public class TcpEndpointTest {
    private static final String HOSTNAME = "myhost";
    private static final String PORT = "45346";
    
    private Map<String, Object> props;
    private Object service;
    
    @Before
    public void defaultProps() {
        props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[]{MyService.class.getName()});
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "");
        service = new MyServiceImpl();
    }

    @Test
    public void testEndpointProperties() throws IOException {
        props.put("aries.rsa.port", PORT);
        props.put("aries.rsa.hostname", HOSTNAME);
        TcpEndpoint tcpEndpoint = new TcpEndpoint(service, props);
        EndpointDescription epd = tcpEndpoint.description();
        Assert.assertEquals("tcp://" + HOSTNAME + ":" + PORT, epd.getId());
        tcpEndpoint.close();
    }
    
    @Test
    public void testEndpointPropertiesDefault() throws IOException {
        TcpEndpoint tcpEndpoint = new TcpEndpoint(service, props);
        EndpointDescription epd = tcpEndpoint.description();
        Assert.assertNotNull(epd.getId());
        tcpEndpoint.close();
    }


}
