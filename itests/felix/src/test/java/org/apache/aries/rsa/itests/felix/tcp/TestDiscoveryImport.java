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
package org.apache.aries.rsa.itests.felix.tcp;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

@RunWith(PaxExam.class)
public class TestDiscoveryImport extends RsaTestBase {
    @Inject
    ZookeeperEndpointRepository publisher;
    
    @Inject
    BundleContext context;

    private Semaphore sem = new Semaphore(0);

    private List<EndpointEvent> events = new ArrayList<>();
    
    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaProviderTcp(),
                rsaDiscoveryZookeeper(),
                localRepo(),
                configZKDiscovery(),
                configZKServer()
        };
    }

    @Test
    public void testDiscoveryImport() throws Exception {
        context.registerService(EndpointEventListener.class, this::endpointChanged, listenerProps());
        EndpointDescription endpoint = createEndpoint();
        publisher.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, endpoint));
        assertTrue(sem.tryAcquire(10, TimeUnit.SECONDS));
        //assertThat(events.get(0), samePropertyValuesAs(new EndpointEvent(EndpointEvent.ADDED, endpoint)));
    }

    private Dictionary<String, Object> listenerProps() {
        Dictionary<String, Object> eprops = new Hashtable<>();
        eprops.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, "(objectClass=*)");
        return eprops;
    }

    private EndpointDescription createEndpoint() {
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[]{"my"});
        props.put(RemoteConstants.ENDPOINT_ID, "myid");
        props.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "myconfig");
        EndpointDescription endpoint = new EndpointDescription(props);
        return endpoint;
    }

    public void endpointChanged(EndpointEvent event, String filter) {
        events.add(event);
        sem.release();
    }
}
