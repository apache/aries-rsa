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
package org.apache.aries.rsa.discovery.zookeeper.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.osgi.framework.BundleContext;

public class ZookeeperStarterTest {

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    BundleContext bc;

    @Mock
    ZookeeperServer server;

    private static QuorumPeerConfig config;

    @InjectMocks
    ZookeeperStarter starter = new ZookeeperStarter() {

        @Override
        protected ZookeeperServer createServer(QuorumPeerConfig config) {
            ZookeeperStarterTest.config = config;
            return server;
        }
    };

    @Captor
    ArgumentCaptor<QuorumPeerConfig> configCaptor;

    @Test
    public void testUpdateConfig() throws Exception {
        final File tempDir = new File("target");
        when(bc.getDataFile("")).thenReturn(tempDir);
        
        Map<String, String> props = new HashMap<>();
        props.put("clientPort", "1234");
        starter.activate(bc, props);

        verify(server, after(1000)).startup();
        verifyConfig(tempDir);

        starter.deactivate();
        
        verify(server).shutdown();
    }

    private void verifyConfig(final File tempDir) {
        assertEquals(1234, config.getClientPortAddress().getPort());
        assertTrue(config.getDataDir().contains(tempDir + File.separator + "zkdata"));
        assertEquals(2000, config.getTickTime());
        assertEquals(10, config.getInitLimit());
        assertEquals(5, config.getSyncLimit());
    }

}
