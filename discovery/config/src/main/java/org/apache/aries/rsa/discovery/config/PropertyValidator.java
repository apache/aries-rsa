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

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;

import java.util.*;

@SuppressWarnings("rawtypes")
class PropertyValidator {
    /**
     * Validates configuration properties,
     * filter out ConfigAdmin specific properties,
     * transforms if needed property types to OSGi CMPN 6. Chapter 122.4
     *
     * @param config configuration properties
     * @return map with validated properties
     */
    static Map<String, Object> validate(Dictionary config) {
        return validatePropertyTypes(filterConfigAdminProperties(toMap(config)));
    }

    static Map<String, Object> validatePropertyTypes(Map<String, Object> map) {
        HashMap<String, Object> result = new HashMap<>();
        if (map != null) {
            for (String key : map.keySet()) {
                if (Constants.OBJECTCLASS.equals(key)) {
                    result.put(key, convertToStringArray(map.get(key)));
                } else {
                    result.put(key, map.get(key));
                }
            }
        }
        return result;
    }

    static String[] convertToStringArray(Object o) {
        String[] result;
        
        if (o == null) {
            result = new String[0];
        } else if (o instanceof List) {
            List list = (List) o;
            result = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = String.valueOf(list.get(i));
            }
        } else if (o.getClass().isArray()) {
            Object[] array = (Object[]) o;
            result = new String[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = String.valueOf(array[i]);
            }
        } else {
            result = new String[1];
            result[0] = String.valueOf(o);
        }
        return result;
    }

    static Map<String, Object> filterConfigAdminProperties(Map<String, ?> map) {
        HashMap<String, Object> result = new HashMap<>();
        if (map != null) {
            for (String key : map.keySet()) {
                if (Constants.SERVICE_PID.equals(key)
                        || ConfigurationAdmin.SERVICE_FACTORYPID.equals(key)
                        || ConfigurationAdmin.SERVICE_BUNDLELOCATION.equals(key)) {
                    continue;
                }
                result.put(key, map.get(key));
            }
        }
        return result;
    }

    static Map<String, Object> toMap(Dictionary dic) {
        HashMap<String, Object> map = new HashMap<>();
        if (dic != null) {
            Enumeration keys = dic.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                map.put(key, dic.get(key));
            }
        }
        return map;
    }
}
