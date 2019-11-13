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

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThat;
import static org.osgi.framework.Version.parseVersion;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.aries.rsa.provider.tcp.myservice.DTOType;
import org.apache.aries.rsa.provider.tcp.myservice.PrimitiveService;
import org.apache.aries.rsa.provider.tcp.myservice.PrimitiveServiceImpl;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.util.EndpointHelper;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

public class TcpProviderPrimitiveTest {

    private static PrimitiveService myServiceProxy;
    private static Endpoint ep;
    
    @BeforeClass
    public static void createServerAndProxy() {
        Class<?>[] exportedInterfaces = new Class[] {PrimitiveService.class};
        TCPProvider provider = new TCPProvider();
        Map<String, Object> props = new HashMap<>();
        EndpointHelper.addObjectClass(props, exportedInterfaces);
        props.put("aries.rsa.hostname", "localhost");
        props.put("aries.rsa.numThreads", "10");
        PrimitiveServiceImpl myService = new PrimitiveServiceImpl();
        BundleContext bc = EasyMock.mock(BundleContext.class);
        ep = provider.exportService(myService, bc, props, exportedInterfaces);
        Assert.assertThat(ep.description().getId(), startsWith("tcp://localhost:"));
        System.out.println(ep.description());
        myServiceProxy = (PrimitiveService)provider.importEndpoint(PrimitiveService.class.getClassLoader(), 
                                                            bc,
                                                            exportedInterfaces, 
                                                            ep.description());
    }

    @Test
    public void testByte() {
        Assert.assertEquals((byte)1, myServiceProxy.callByte((byte) 1));
    }
    
    @Test
    public void testShort() {
        Assert.assertEquals((short)1, myServiceProxy.callShort((short) 1));
    }
    
    @Test
    public void testInteger() {
        Assert.assertEquals(1, myServiceProxy.callInt(1));
    }
    
    @Test
    public void testLong() {
        Assert.assertEquals(1L, myServiceProxy.callLong(1L));
    }

    @Test
    public void testFloat() {
        Assert.assertEquals(1f, myServiceProxy.callFloat(1f), 0.001);
    }
    
    @Test
    public void testDouble() {
        Assert.assertEquals(1d, myServiceProxy.callDouble(1d), 0.001);
    }
    
    @Test
    public void testBoolean() {
        Assert.assertEquals(true, myServiceProxy.callBoolean(true));
    }
    
    @Test
    public void testByteAr() {
        Assert.assertArrayEquals(new byte[]{1}, myServiceProxy.callByteAr(new byte[]{1}));
    }
    
    @Test
    public void testVersion() {
        assertThat(myServiceProxy.callVersion(parseVersion("1.0.0")), equalTo(parseVersion("1.0.0")));
    }
    
    @Test
    public void testVersionAr() {
        assertThat(myServiceProxy.callVersionAr(new Version[] {parseVersion("1.0.0")}), equalTo(new Version[] {parseVersion("1.0.0")}));
    }
    
    @Test
    public void testVersionList() {
        assertThat(myServiceProxy.callVersionList(Arrays.asList(parseVersion("1.0.0"))), equalTo(Arrays.asList(parseVersion("1.0.0"))));
    }
    
    @Test
    public void testVersionSet() {
        Set<Version> set = new HashSet<>(asList(parseVersion("1.0.0")));
        assertThat(myServiceProxy.callVersionSet(set), everyItem(isIn(set)));
    }
    
    @Test
    public void testVersionMap() {
        HashMap<Version, Version> map = new HashMap<>();
        map.put(parseVersion("1.2.3"), parseVersion("2.3.4"));
        assertThat(myServiceProxy.callVersionMap(map).entrySet(), everyItem(isIn(map.entrySet())));
    }

    /**
     * TODO DTOs seem to cause stack overflow at least on the apache jenkins (linux).
     * On a Mac this seems to work.
     */
    @Ignore
    @Test
    public void testDTO() {
        DTOType dto = new DTOType();
        dto.value = "Test";
        assertThat(myServiceProxy.callDTO(dto), samePropertyValuesAs(dto));
    }
    
    @Ignore
    @Test
    public void testDTOAr() {
        DTOType dto = new DTOType();
        dto.value = "Test";
        DTOType[] dtoAr = new DTOType[] {dto};
        DTOType[] result = myServiceProxy.callDTOAr(dtoAr);
        assertThat(result[0], samePropertyValuesAs(dtoAr[0]));
    }
    
    @AfterClass
    public static void close() throws IOException {
        ep.close();
    }

}
