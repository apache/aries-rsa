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
package org.apache.aries.rsa.itests.felix.discovery.config;

import org.apache.aries.rsa.examples.echotcp.api.EchoService;
import org.apache.aries.rsa.itests.felix.RsaTestBase;
import org.apache.aries.rsa.itests.felix.ServerConfiguration;
import org.apache.aries.rsa.itests.felix.TwoContainerPaxExam;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import javax.inject.Inject;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

@RunWith(TwoContainerPaxExam.class)
public class TestConfigDiscoveryRoundTrip extends RsaTestBase {

    @Inject
    EchoService echoService;

    @ServerConfiguration
    public static Option[] remoteConfig() throws IOException {
        return new Option[] //
        {
         rsaCore(), //
         rsaProviderTcp(), //
         echoTcpService()
        };
    }

    @Configuration
    public static Option[] configure() throws Exception {
        return new Option[] //
        {
         rsaCore(), //
         rsaDiscoveryConfig(), //
         rsaProviderTcp(), //
         echoTcpConsumer(), //
         configImportEchoService()
        };
    }

    protected static Option configImportEchoService() {
        return factoryConfiguration("org.apache.aries.rsa.discovery.config")
            .put("service.imported", "true")
            .put("service.imported.configs", "aries.tcp")
            .put("objectClass", "org.apache.aries.rsa.examples.echotcp.api.EchoService")
            .put("endpoint.id", "tcp://localhost:8201")
            .put("aries.tcp.hostname", "localhost")
            .put("aries.tcp.port", "8201")
            .asOption();
    }

    @Test
    public void testCall() throws Exception {
        assertEquals("test", echoService.echo("test"));
    }

}
