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
package org.apache.aries.rsa.discovery.config;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import static java.util.Collections.singletonList;
import static org.apache.aries.rsa.discovery.config.PropertyValidator.*;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author dpishchukhin
 */
public class PropertyValidatorTest {
    @Test
    public void testToMap() throws Exception {
        Dictionary<String, String> dic = new Hashtable<>();
        dic.put("key", "value");

        assertThat(toMap(dic).size(), is(1));
        assertThat(toMap(dic).keySet().contains("key"), is(true));
        assertThat(toMap(dic).get("key"), Is.<Object>is("value"));

        assertThat(toMap(null), notNullValue());
        assertThat(toMap(null).size(), is(0));
    }

    @Test
    public void testFilterConfigAdminProperties() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put(Constants.SERVICE_PID, "testPid");
        map.put(ConfigurationAdmin.SERVICE_FACTORYPID, "factoryPid");
        map.put(ConfigurationAdmin.SERVICE_BUNDLELOCATION, "bundleLocation");

        assertThat(filterConfigAdminProperties(map).size(), is(0));
        assertThat(filterConfigAdminProperties(null), notNullValue());
        assertThat(filterConfigAdminProperties(null).size(), is(0));
    }

    @Test
    public void testValidatePropertyTypes_null_param() throws Exception {
        assertThat(validatePropertyTypes(null), notNullValue());
        assertThat(validatePropertyTypes(null).size(), is(0));
    }

    @Test
    public void testValidatePropertyTypes_objectClass() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put(Constants.OBJECTCLASS, "test");
        Map<String, Object> config = validatePropertyTypes(map);
        assertThat(config.containsKey(Constants.OBJECTCLASS), is(true));
        assertThat(config.get(Constants.OBJECTCLASS), Is.<Object>is(new String[]{"test"}));

        map = new HashMap<>();
        map.put(Constants.OBJECTCLASS, new String[]{"test"});
        config = validatePropertyTypes(map);
        assertThat(config.get(Constants.OBJECTCLASS), Is.<Object>is(new String[]{"test"}));

        map = new HashMap<>();
        map.put(Constants.OBJECTCLASS, singletonList("test"));
        config = validatePropertyTypes(map);
        assertThat(config.get(Constants.OBJECTCLASS), Is.<Object>is(new String[]{"test"}));
    }

    @Test
    public void testConvertToStringArray() throws Exception {
        assertThat(convertToStringArray(null), Is.<Object>is(new String[0]));
        assertThat(convertToStringArray("test"), Is.<Object>is(new String[]{"test"}));
        assertThat(convertToStringArray(new String[]{"test"}), Is.<Object>is(new String[]{"test"}));
        assertThat(convertToStringArray(singletonList("test")), Is.<Object>is(new String[]{"test"}));
        assertThat(convertToStringArray(new Vector<>(singletonList("test"))), Is.<Object>is(new String[]{"test"}));
        assertThat(convertToStringArray(1), Is.<Object>is(new String[]{"1"}));
    }
}
