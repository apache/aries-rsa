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

import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;


public class EndpointParserTest {
    
    private Map<String, Object> props;

    @Before
    public void defaultProps() {
        props = new Hashtable<>();
        props.put("objectClass", new String[]{Runnable.class.getName()});
        props.put(RemoteConstants.ENDPOINT_ID, "myid");
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "any");
    }

    @Test
    public void testDefaults() {
        Assert.assertEquals(300000, getParser().getTimeoutMillis());
        Assert.assertEquals(0, getParser().getPort());
        Assert.assertEquals(LocalHostUtil.getLocalIp(), getParser().getHostname());
    }

    @Test
    public void testTimeoutString() {
        props.put(EndpointPropertiesParser.TIMEOUT_KEY, "100");
        Assert.assertEquals(100, getParser().getTimeoutMillis());
    }
    
    @Test
    public void testTimeoutInt() {
        props.put(EndpointPropertiesParser.TIMEOUT_KEY, 100);
        Assert.assertEquals(100, getParser().getTimeoutMillis());
    }

    
    @Test
    public void testPortString() {
        props.put(EndpointPropertiesParser.PORT_KEY, "11111");
        Assert.assertEquals(11111, getParser().getPort());
    }
    
    @Test
    public void testPortInt() {
        props.put(EndpointPropertiesParser.PORT_KEY, 11111);
        Assert.assertEquals(11111, getParser().getPort());
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testTimeoutInvalid() {
        props.put(EndpointPropertiesParser.TIMEOUT_KEY, new Date());
        getParser().getTimeoutMillis();
    }
    
    private EndpointPropertiesParser getParser() {
        return new EndpointPropertiesParser(new EndpointDescription(props));
    }

}
