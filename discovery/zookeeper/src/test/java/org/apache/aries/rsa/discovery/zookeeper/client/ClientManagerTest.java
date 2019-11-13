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
package org.apache.aries.rsa.discovery.zookeeper.client;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.discovery.zookeeper.client.ClientManager;
import org.apache.aries.rsa.discovery.zookeeper.client.ClientManager.DiscoveryConfig;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.util.converter.Converters;

public class ClientManagerTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    
    @Mock
    BundleContext context;
    
    @Mock
    ZooKeeper zookeeper;
    
    Semaphore sem = new Semaphore(0);
    
    private DiscoveryConfig config;
    private ClientManager zkd;
    
    @Before
    public void before() {
        zkd = new ClientManager() {
            @Override
            protected ZooKeeper createClient(DiscoveryConfig config) {
                ClientManagerTest.this.config = config;
                sem.release();
                return zookeeper;
            }  
        };
    }
    
    @Test
    public void testDefaults() throws ConfigurationException, InstantiationException, IllegalAccessException, InterruptedException {
        Map<String, Object> configuration = new HashMap<>();
        
        zkd.activate(convert(configuration), context);
        
        sem.tryAcquire(10, TimeUnit.SECONDS);
        assertEquals("localhost", config.zookeeper_host());
        assertEquals("2181", config.zookeeper_port());
        assertEquals(3000, config.zookeeper_timeout());
    }

    @Test
    public void testConfig() throws ConfigurationException, InterruptedException {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("zookeeper.host", "myhost");
        configuration.put("zookeeper.port", "1");
        configuration.put("zookeeper.timeout", "1000");
        
        DiscoveryConfig config2 = convert(configuration);
        assertEquals("myhost", config2.zookeeper_host());
        zkd.activate(config2, context);
        
        sem.tryAcquire(10, TimeUnit.SECONDS);
        assertEquals("myhost", config.zookeeper_host());
        assertEquals("1", config.zookeeper_port());
        assertEquals(1000, config.zookeeper_timeout());
    }

    private DiscoveryConfig convert(Map<String, Object> configuration) {
        return Converters.standardConverter().convert(configuration).to(DiscoveryConfig.class);
    }
}
