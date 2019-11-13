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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.apache.aries.rsa.itests.felix.ServerConfiguration;
import org.apache.aries.rsa.itests.felix.TwoContainerPaxExam;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

@RunWith(TwoContainerPaxExam.class)
public class TestRoundTrip extends RsaTestBase {

    @Inject
    EchoService echoService;

    @ServerConfiguration
    public static Option[] remoteConfig() throws IOException {
        return new Option[] {
            rsaCore(),
            rsaDiscoveryZookeeper(),
            rsaProviderTcp(),
            echoTcpService(),
            configZKServer(),
            configZKDiscovery(),
        };
    }

    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] {
                rsaCore(),
                rsaDiscoveryZookeeper(),
                rsaProviderTcp(),
                echoTcpConsumer(),
                configZKDiscovery()
        };
    }

    @Test
    public void testCall() throws Exception {
        assertEquals("test", echoService.echo("test"));
    }

}
