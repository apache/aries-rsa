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

package org.apache.aries.rsa.itests.felix;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.OptionalCompositeOption;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;

import javax.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

public class RsaTestBase {
    protected static final String ZK_PORT = "15201";

    @Inject
    protected BundleContext bundleContext;

    @Inject
    ConfigurationAdmin configAdmin;

    protected static OptionalCompositeOption localRepo() {
        String localRepo = System.getProperty("maven.repo.local");
        if (localRepo == null) {
            localRepo = System.getProperty("org.ops4j.pax.url.mvn.localRepository");
        }
        return when(localRepo != null)
            .useOptions(vmOption("-Dorg.ops4j.pax.url.mvn.localRepository=" + localRepo));
    }

    protected static MavenArtifactProvisionOption mvn(String groupId, String artifactId) {
        return mavenBundle().groupId(groupId).artifactId(artifactId).versionAsInProject();
    }

    public void testInstalled() throws Exception {
        for (Bundle bundle : bundleContext.getBundles()) {
            System.out.println(bundle.getBundleId() + " " + bundle.getSymbolicName() + " " + bundle.getState()
                               + " " + bundle.getVersion());
        }
    }

    protected int getFreePort() throws IOException {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true); // enables quickly reopening socket on same port
            socket.bind(new InetSocketAddress(0)); // zero finds a free port
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    protected static Option echoTcpConsumer() {
        return CoreOptions.composite(
        mvn("org.apache.felix", "org.apache.felix.scr"),
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.api"),
        // Consumer is needed to trigger service import. Pax exam inject does not trigger it
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.consumer")
        );
    }

    protected static Option echoTcpService() {
        return composite(
        mvn("org.apache.felix", "org.apache.felix.scr"),
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.api"),
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.service")
        );
    }

    protected static Option rsaCoreZookeeper() {
        return composite(junitBundles(), 
                         localRepo(),
                         systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
                         systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
                         systemProperty("zkPort").value("15201"),
                         systemProperty("aries.rsa.hostname").value("localhost"),
                         mvn("org.apache.felix", "org.apache.felix.configadmin"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.core"), 
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.spi"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.topology-manager"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.local"),
                         mvn("org.apache.zookeeper", "zookeeper"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.zookeeper")
                         //CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
            );
    }

    protected static Option rsaCoreConfigDiscovery() {
        return composite(junitBundles(),
                         localRepo(),
                         systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
                         systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
                         systemProperty("aries.rsa.hostname").value("localhost"),
                         mvn("org.apache.felix", "org.apache.felix.configadmin"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.core"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.spi"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.topology-manager"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.config")
                         //CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
            );
    }

    protected static Option rsaCore() {
        return composite(junitBundles(),
                         localRepo(),
                         systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
                         systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
                         systemProperty("aries.rsa.hostname").value("localhost"),
                         mvn("org.apache.felix", "org.apache.felix.configadmin"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.core"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.spi"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.topology-manager"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.local")
                         //CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
            );
    }

    protected static Option rsaTcp() {
        return mvn("org.apache.aries.rsa.provider", "org.apache.aries.rsa.provider.tcp");
    }

    protected static Option rsaFastBin() {
        return composite(mvn("org.fusesource.hawtbuf", "hawtbuf"),
                       mvn("org.fusesource.hawtdispatch", "hawtdispatch"),
                       mvn("org.apache.aries.rsa.provider", "org.apache.aries.rsa.provider.fastbin"));
    }

    protected static Option configZKConsumer() {
        return newConfiguration("org.apache.aries.rsa.discovery.zookeeper")
            .put("zookeeper.host", "127.0.0.1")
            .put("zookeeper.port", ZK_PORT)
            .asOption();
    }

    protected static Option configTcpConfigDiscovery() {
        return factoryConfiguration("org.apache.aries.rsa.discovery.config")
            .put("service.imported", "true")
            .put("service.imported.configs", "aries.tcp")
            .put("objectClass", "org.apache.aries.rsa.examples.echotcp.api.EchoService")
            .put("endpoint.id", "tcp://localhost:8201")
            .put("aries.tcp.hostname", "localhost")
            .put("aries.tcp.port", "8201")
            .asOption();
    }

    protected static Option configZKServer() {
        return newConfiguration("org.apache.aries.rsa.discovery.zookeeper.server")
            .put("clientPort", ZK_PORT).asOption();
    } 
    
    protected static Option configFastBin(String port) {
        return newConfiguration("org.apache.aries.rsa.provider.fastbin")
            .put("uri", "tcp://0.0.0.0:" + port).asOption();
    }

}
