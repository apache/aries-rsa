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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.osgi.framework.Constants;

public class OverlayPropertiesTest {
    
    @Test
    public void testOverlayProperties() {
        Map<String, Object> sProps = new HashMap<>();
        Map<String, Object> aProps = new HashMap<>();

        RemoteServiceAdminCore.overlayProperties(sProps, aProps);
        assertEquals(0, sProps.size());

        sProps.put("aaa", "aval");
        sProps.put("bbb", "bval");
        sProps.put(Constants.OBJECTCLASS, new String[] {"X"});
        sProps.put(Constants.SERVICE_ID, 17L);

        aProps.put("AAA", "achanged");
        aProps.put("CCC", "CVAL");
        aProps.put(Constants.OBJECTCLASS, new String[] {"Y"});
        aProps.put(Constants.SERVICE_ID.toUpperCase(), 51L);

        Map<String, Object> aPropsOrg = new HashMap<>(aProps);
        RemoteServiceAdminCore.overlayProperties(sProps, aProps);
        assertEquals("The additional properties should not be modified", aPropsOrg, aProps);

        assertEquals(5, sProps.size());
        assertEquals("achanged", sProps.get("aaa"));
        assertEquals("bval", sProps.get("bbb"));
        assertEquals("CVAL", sProps.get("CCC"));
        assertTrue("Should not be possible to override the objectClass property",
                Arrays.equals(new String[] {"X"}, (Object[]) sProps.get(Constants.OBJECTCLASS)));
        assertEquals("Should not be possible to override the service.id property",
                17L, sProps.get(Constants.SERVICE_ID));
    }
    
    @Test
    public void testOverlayProperties2() {
        Map<String, Object> original = new HashMap<>();

        original.put("MyProp", "my value");
        original.put(Constants.OBJECTCLASS, "myClass");

        Map<String, Object> copy = new HashMap<>();
        copy.putAll(original);

        // nothing should change here
        Map<String, Object> overload = new HashMap<>();
        RemoteServiceAdminCore.overlayProperties(copy, overload);

        assertEquals(original.size(), copy.size());
        for (Object key : original.keySet()) {
            assertEquals(original.get(key), copy.get(key));
        }

        copy.clear();
        copy.putAll(original);

        // a property should be added
        overload = new HashMap<>();
        overload.put("new", "prop");

        RemoteServiceAdminCore.overlayProperties(copy, overload);

        assertEquals(original.size() + 1, copy.size());
        for (Object key : original.keySet()) {
            assertEquals(original.get(key), copy.get(key));
        }
        assertNotNull(overload.get("new"));
        assertEquals("prop", overload.get("new"));

        copy.clear();
        copy.putAll(original);

        // only one property should be added
        overload = new HashMap<>();
        overload.put("new", "prop");
        overload.put("NEW", "prop");

        RemoteServiceAdminCore.overlayProperties(copy, overload);

        assertEquals(original.size() + 1, copy.size());
        for (Object key : original.keySet()) {
            assertEquals(original.get(key), copy.get(key));
        }
        assertNotNull(overload.get("new"));
        assertEquals("prop", overload.get("new"));

        copy.clear();
        copy.putAll(original);

        // nothing should change here
        overload = new HashMap<>();
        overload.put(Constants.OBJECTCLASS, "assd");
        overload.put(Constants.SERVICE_ID, "asasdasd");
        RemoteServiceAdminCore.overlayProperties(copy, overload);

        assertEquals(original.size(), copy.size());
        for (Object key : original.keySet()) {
            assertEquals(original.get(key), copy.get(key));
        }

        copy.clear();
        copy.putAll(original);

        // overwrite own prop
        overload = new HashMap<>();
        overload.put("MyProp", "newValue");
        RemoteServiceAdminCore.overlayProperties(copy, overload);

        assertEquals(original.size(), copy.size());
        for (Object key : original.keySet()) {
            if (!"MyProp".equals(key)) {
                assertEquals(original.get(key), copy.get(key));
            }
        }
        assertEquals("newValue", copy.get("MyProp"));

        copy.clear();
        copy.putAll(original);

        // overwrite own prop in different case
        overload = new HashMap<>();
        overload.put("MYPROP", "newValue");
        RemoteServiceAdminCore.overlayProperties(copy, overload);

        assertEquals(original.size(), copy.size());
        for (Object key : original.keySet()) {
            if (!"MyProp".equals(key)) {
                assertEquals(original.get(key), copy.get(key));
            }
        }
        assertEquals("newValue", copy.get("MyProp"));
    }
}
