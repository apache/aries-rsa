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
package org.apache.aries.rsa.topologymanager.importer;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Minimal implementation of a synchronized map
 */
public class MultiMap<T> {

    private Map<String, Set<T>> map;
    
    public MultiMap() {
        map = new HashMap<>();
    }
    
    public synchronized void put(String key, T value) {
        Set<T> values = map.get(key);
        if (values == null) {
            values = Collections.synchronizedSet(new HashSet<T>());
            map.put(key, values);
        }
        values.add(value);
    }
    
    public synchronized Set<T> get(String key) {
        return map.getOrDefault(key, Collections.<T>emptySet());
    }

    public synchronized void remove(String key, T value) {
        Set<T> values = map.get(key);
        values.remove(value);
        if (values.isEmpty()) {
            map.remove(key);
        }
    }

    public synchronized Set<String> keySet() {
        return map.keySet();
    }

    public void remove(T toRemove) {
        Set<String> keys = new HashSet<>(map.keySet());
        for (String key : keys) {
            remove(key, toRemove);
        }
    }

    public synchronized void clear() {
        map.clear();
    }
}
