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
package org.apache.aries.rsa.discovery.zookeeper;

import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.apache.zookeeper.KeeperException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

@RunWith(MockitoJUnitRunner.class)
public class PublishingEndpointListenerTest {
    @Mock
    ZookeeperEndpointRepository repository;

    @InjectMocks
    PublishingEndpointListener eli;

    @Test
    public void testEndpointRemovalAdding() throws KeeperException, InterruptedException {
        EndpointDescription endpoint = createEndpoint();
        EndpointEvent event1 = new EndpointEvent(EndpointEvent.ADDED, endpoint);
        eli.endpointChanged(event1, null);
        EndpointEvent event2 = new EndpointEvent(EndpointEvent.REMOVED, endpoint);
        eli.endpointChanged(event2, null);
        
        verify(repository).endpointChanged(event1);
        verify(repository).endpointChanged(event2);
    }

    private EndpointDescription createEndpoint() {
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[] {"myClass"});
        props.put(RemoteConstants.ENDPOINT_ID, "http://google.de:80/test/sub");
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "myConfig");
        return new EndpointDescription(props);
    }
}
