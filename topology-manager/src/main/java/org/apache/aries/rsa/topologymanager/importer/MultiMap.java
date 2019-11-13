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
package org.apache.aries.rsa.topologymanager.importer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Minimal implementation of a thread-safe map where each key can have multiple values.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class MultiMap<K, V> {

    private Map<K, Set<V>> map;
    
    public MultiMap() {
        map = new ConcurrentHashMap<>();
    }
    
    public void put(K key, V value) {
        map.compute(key, (k, v) -> {
            if (v == null) {
                v = new CopyOnWriteArraySet<>();
            }
            v.add(value);
            return v;
        });
    }

    public Set<V> get(K key) {
        Set<V> values = map.get(key);
        return values == null ? Collections.emptySet() : Collections.unmodifiableSet(values);
    }

    public void remove(K key, V value) {
        // reminder: returning null from the compute lambda will remove the mapping
        map.compute(key, (k, v) -> v != null && v.remove(value) && v.isEmpty() ? null : v);
    }

    public void remove(V value) {
        for (K key : map.keySet()) {
            remove(key, value);
        }
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public void clear() {
        map.clear();
    }
}
