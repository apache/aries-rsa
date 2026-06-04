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
package org.apache.aries.rsa.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collection-related utilities that can be useful to SPI implementations.
 */
public class CollectionUtils {

    // private constructor to prevent instantiation
    private CollectionUtils() {}

    /**
     * Returns the union of all elements in all given collections.
     *
     * @param <T> the type of items in the collections
     * @param collections the collections to combine
     * @return the union of all elements in all the collections
     */
    @SafeVarargs
    public static <T> Set<T> union(Collection<? extends T>... collections) {
        Set<T> union = new HashSet<>();
        for (Collection<? extends T> c : collections)
            if (c != null)
                union.addAll(c);
        return union;
    }

    /**
     * Returns the set of keys whose entries have changed between two maps
     * (added, removed, or changed value).
     *
     * @param p1 the first map
     * @param p2 the second map
     * @return the set of changed keys
     */
    public static Set<String> getChangedKeys(Map<String, Object> p1, Map<String, Object> p2) {
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : p1.entrySet()) {
            Object v = p2.get(entry.getKey());
            if (!Objects.deepEquals(entry.getValue(), v) || v == null && !p2.containsKey(entry.getKey()))
                changed.add(entry.getKey());
        }
        for (String k : p2.keySet()) {
            if (!p1.containsKey(k))
                changed.add(k);
        }
        return changed;
    }
}
