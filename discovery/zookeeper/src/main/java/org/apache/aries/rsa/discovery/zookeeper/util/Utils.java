/**
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
package org.apache.aries.rsa.discovery.zookeeper.util;

import java.util.ArrayList;
import java.util.List;

public final class Utils {

    static final String PATH_PREFIX = "/osgi/service_registry";

    private Utils() {
        // never constructed
    }

    public static String getZooKeeperPath(String name) {
        if (name != null && name.startsWith(".")) {
            name = name.substring(1);
        }

        return name == null || name.isEmpty() ? PATH_PREFIX : PATH_PREFIX + '/' + name.replace('.', '/');
    }

    /**
     * Removes nulls and empty strings from the given string array.
     *
     * @param strings an array of strings
     * @return a new array containing the non-null and non-empty
     *         elements of the original array in the same order
     */
    public static List<String> removeEmpty(List<String> strings) {
        List<String> result = new ArrayList<String>();
        if (strings == null) {
            return result;
        }
        for (String s : strings) {
            if (s != null && !s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }


}
