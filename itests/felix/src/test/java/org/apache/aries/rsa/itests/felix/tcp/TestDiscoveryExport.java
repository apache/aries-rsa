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

import static org.awaitility.Awaitility.await;

import java.io.ByteArrayInputStream;

import javax.inject.Inject;

import org.apache.aries.rsa.discovery.zookeeper.client.ZookeeperEndpointRepository;
import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.service.remoteserviceadmin.EndpointDescription;

@RunWith(PaxExam.class)
public class TestDiscoveryExport extends RsaTestBase {
    @Inject
    DistributionProvider tcpProvider;
    
    @Inject
    EndpointDescriptionParser parser;
    
    @Inject
    ZooKeeper zookeeper;

    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaDiscoveryZookeeper(),
                rsaProviderTcp(),
                echoTcpService(),
                localRepo(),
                configZKDiscovery(),
                configZKServer()
        };
    }

    @Test
    public void testDiscoveryExport() throws Exception {
        EndpointDescription epd = getEndpoint();
        EchoService service = (EchoService)tcpProvider
            .importEndpoint(EchoService.class.getClassLoader(), 
                            bundleContext, new Class[]{EchoService.class}, epd);
        Assert.assertEquals("test", service.echo("test"));
    }

    private EndpointDescription getEndpoint() throws Exception {
        String endpointName = await("Node exists").until(this::getEndpointPath, Matchers.notNullValue());
        return getEndpointDescription(zookeeper, ZookeeperEndpointRepository.PATH_PREFIX + "/" + endpointName);
    }

    private EndpointDescription getEndpointDescription(ZooKeeper zk, String endpointPath)
        throws KeeperException, InterruptedException {
        byte[] data = zk.getData(endpointPath, false, null);
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        return parser.readEndpoint(is);
    }

    private String getEndpointPath() throws KeeperException, InterruptedException {
        return zookeeper.getChildren(ZookeeperEndpointRepository.PATH_PREFIX, false).stream()
                .findFirst()
                .orElse(null);
    }

}
