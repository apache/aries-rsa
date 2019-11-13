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
package org.apache.aries.rsa.discovery.endpoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.xml.sax.InputSource;

public class PropertiesMapperTest {
    @Test
    @Ignore
    public void testCreateXML() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service.imported.configs", "org.apache.cxf.ws");
        m.put("endpoint.id", "foo:bar");
        m.put("objectClass", new String[] {"com.acme.HelloService", "some.other.Service"});
        m.put("SomeObject", new Object());
        m.put("long", 9223372036854775807L);
        m.put("Long2", -1L);
        m.put("double", 1.7976931348623157E308);
        m.put("Double2", 1.0d);
        m.put("float", 42.24f);
        m.put("Float2", 1.0f);
        m.put("int", 17);
        m.put("Integer2", 42);
        m.put("byte", (byte) 127);
        m.put("Byte2", (byte) -128);
        m.put("boolean", true);
        m.put("Boolean2", false);
        m.put("short", (short) 99);
        m.put("Short2", (short) -99);
        m.put("char", '@');
        m.put("Character2", 'X');

        m.put("bool-list", Arrays.asList(true, false));
        m.put("empty-set", new HashSet<>());

        Set<String> stringSet = new LinkedHashSet<>();
        stringSet.add("Hello there");
        stringSet.add("How are you?");
        m.put("string-set", stringSet);

        int[] intArray = new int[] {1, 2};
        m.put("int-array", intArray);

        String xml = "<xml>\n"
            + "<t1 xmlns=\"http://www.acme.org/xmlns/other/v1.0.0\">\n"
            + "<foo type='bar'>haha</foo>\n"
            + "</t1>\n"
            + "</xml>";
        m.put("someXML", xml);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EndpointDescription epd = new EndpointDescription(m);
        new EndpointDescriptionParserImpl().writeEndpoint(epd, bos);
        byte[] epData = bos.toByteArray();
        System.out.println(new String(epData));
        URL edURL = getClass().getResource("/ed2-generated.xml");
        InputSource expectedXml = new InputSource(edURL.openStream());
        InputSource actualXml = new InputSource(new ByteArrayInputStream(epData)); 
        XMLAssert.assertXMLEqual(expectedXml, actualXml);
    }

}
