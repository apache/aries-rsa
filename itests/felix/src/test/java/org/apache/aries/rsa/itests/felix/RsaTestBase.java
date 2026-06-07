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
package org.apache.aries.rsa.itests.felix;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import javax.inject.Inject;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.MavenUtils;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.OptionalCompositeOption;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;

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

    protected static int getFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true); // enables quickly reopening socket on same port
            socket.bind(new InetSocketAddress(0)); // zero finds a free port
            return socket.getLocalPort();
        }
    }

    protected Bundle getBundle(String symName) {
        Bundle serviceBundle = null;
        Bundle[] bundles = bundleContext.getBundles();
        for (Bundle bundle : bundles) {
            if (symName.equals(bundle.getSymbolicName())) {
                serviceBundle = bundle;
                break;
            }
        }
        return serviceBundle;
    }

    protected static Option echoTcpAPI() {
        return mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.api");
    }

    protected static Option echoTcpConsumer() {
        return CoreOptions.composite(
        echoTcpAPI(),
        // Consumer bundle is needed to trigger service import. Pax exam inject does not trigger it
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.consumer")
        );
    }

    protected static Option echoTcpService() {
        return composite(
        echoTcpAPI(),
        mvn("org.apache.aries.rsa.examples.echotcp", "org.apache.aries.rsa.examples.echotcp.service")
        );
    }

    protected static Option junit() {
        // we use a custom configuration and not CoreOptions.junitBundles
        // because so hamcrest is not too old and not too new for awaitility
        String junitVersion = MavenUtils.getArtifactVersion("junit", "junit");
        String hamcrestVersion = MavenUtils.getArtifactVersion("org.hamcrest", "hamcrest");
        return composite(
            wrappedBundle(mavenBundle("junit", "junit", junitVersion)).instructions(
                "Bundle-SymbolicName=junit",
                "Export-Package=org.junit.*;version=" + junitVersion + ",junit.*;version=" + junitVersion,
                "Import-Package=org.hamcrest;version=\"" + hamcrestVersion
                    + "\",org.hamcrest.core;version=\"" + hamcrestVersion + "\""
            ),
            mvn("org.hamcrest", "hamcrest"),
            mvn("org.awaitility", "awaitility"));
    }

    protected static Option rsaCore() {
        return composite(junit(),
                         localRepo(),
                         logback(),
                         systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
                         systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
                         systemProperty("aries.tcp.hostname").value("localhost"),
                         mvn("org.osgi", "org.osgi.util.function"),
                         mvn("org.osgi", "org.osgi.util.promise"),
                         mvn("org.osgi", "org.osgi.service.component"),
                         mvn("org.apache.aries.spifly", "org.apache.aries.spifly.dynamic.bundle"),
                         mvn("org.ow2.asm", "asm"),
                         mvn("org.ow2.asm", "asm-commons"),
                         mvn("org.ow2.asm", "asm-util"),
                         mvn("org.ow2.asm", "asm-tree"),
                         mvn("org.ow2.asm", "asm-analysis"),
                         mvn("jakarta.xml.bind", "jakarta.xml.bind-api"),
                         mvn("com.sun.xml.bind", "jaxb-osgi"),
                         mvn("jakarta.activation", "jakarta.activation-api"),
                         mvn("org.apache.felix", "org.apache.felix.eventadmin"),
                         mvn("org.apache.felix", "org.apache.felix.configadmin"),
                         mvn("org.apache.felix", "org.apache.felix.scr"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.core"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.spi"),
                         mvn("org.apache.aries.rsa", "org.apache.aries.rsa.topology-manager"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.local")
        );
    }

    public static Option logback() {
        return composite(systemProperty("logback.configurationFile").value("src/test/resources/logback.xml"),
                mvn("org.slf4j", "slf4j-api"),
                mvn("ch.qos.logback", "logback-core"),
                mvn("ch.qos.logback", "logback-classic"));
    }

    protected static Option debug() {
        return CoreOptions.vmOption("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
    }

    protected static Option rsaDiscoveryConfig() {
        return composite(mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.config"));
    }

    protected static Option rsaDiscoveryZookeeper() {
        return composite(mvn("io.netty", "netty-handler"),
                         mvn("io.netty", "netty-buffer"),
                         mvn("io.netty", "netty-transport"),
                         mvn("io.netty", "netty-common"),
                         mvn("io.netty", "netty-resolver"),
                         mvn("io.netty", "netty-transport-native-unix-common"),
                         mvn("io.netty", "netty-codec"),
                         mvn("io.dropwizard.metrics", "metrics-core"),
                         mvn("org.xerial.snappy", "snappy-java"),
                         mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.zookeeper"));
    }

    protected static Option rsaDiscoveryTcp() {
        return composite(
            mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.tcp"));
    }

    protected static Option rsaProviderTcp() {
        return mvn("org.apache.aries.rsa.provider", "org.apache.aries.rsa.provider.tcp");
    }

    protected static Option rsaProviderFastBin() {
        return composite(mvn("org.fusesource.hawtbuf", "hawtbuf"),
                         mvn("org.fusesource.hawtdispatch", "hawtdispatch"),
                         mvn("org.apache.aries.rsa.provider", "org.apache.aries.rsa.provider.fastbin"));
    }

    protected static Option rsaDiscoveryMdns() {
        return composite(
                mvn("org.ops4j.pax.logging", "pax-logging-api"),
                mvn("org.ops4j.pax.logging", "pax-logging-logback"),
                mvn("org.jmdns", "jmdns"),
                mvn("org.apache.aries.spec", "org.apache.aries.javax.jax.rs-api"),
                mvn("org.apache.aries.component-dsl", "org.apache.aries.component-dsl.component-dsl"),
                mvn("org.apache.aries.jax.rs", "org.apache.aries.jax.rs.whiteboard"),
                mvn("jakarta.xml.bind", "jakarta.xml.bind-api"),
                mvn("jakarta.activation", "jakarta.activation-api"),
                mavenBundle("jakarta.activation", "jakarta.activation-api", "1.2.2"),
                mavenBundle("jakarta.xml.bind", "jakarta.xml.bind-api", "2.3.3"),
                mvn("com.fasterxml.woodstox", "woodstox-core"),
                mvn("org.codehaus.woodstox", "stax2-api"),
                mvn("com.sun.istack", "istack-commons-runtime"),
                mvn("org.glassfish.jaxb", "jaxb-runtime"),
                mvn("org.apache.geronimo.specs", "geronimo-servlet_3.0_spec"),
                mvn("org.apache.geronimo.specs", "geronimo-annotation_1.3_spec"),
                mvn("org.apache.geronimo.specs", "geronimo-jaxws_2.2_spec"),
                mvn("org.apache.geronimo.specs", "geronimo-saaj_1.3_spec"),
                mvn("org.apache.ws.xmlschema", "xmlschema-core"),
                mvn("org.apache.cxf", "cxf-core"),
                mvn("org.apache.cxf", "cxf-rt-features-logging"),
                mvn("org.apache.cxf", "cxf-rt-frontend-jaxrs"),
                mvn("org.apache.cxf", "cxf-rt-rs-client"),
                mvn("org.apache.cxf", "cxf-rt-security"),
                mvn("org.apache.cxf", "cxf-rt-transports-http"),
                mvn("org.apache.cxf", "cxf-rt-rs-sse"),
                mvn("org.osgi", "org.osgi.service.http.whiteboard"),
                mvn("org.osgi", "org.osgi.service.jaxrs"),
                mvn("org.apache.felix", "org.apache.felix.http.servlet-api"),
                mvn("org.apache.felix", "org.apache.felix.http.jetty"),
                mvn("org.apache.aries.rsa.discovery", "org.apache.aries.rsa.discovery.mdns"));
    }

    protected static Option configTcpDiscovery(int instance, int peerInstance) {
        return newConfiguration("org.apache.aries.rsa.discovery.tcp") //
                .put("address", "127.0.0.1:" + (7667 + instance)) //
                .put("peers", "127.0.0.1:" + (7667 + peerInstance)) //
                .asOption();
    }

    protected static Option configMdnsDiscovery() throws IOException {
        int port = getFreePort();
        return systemProperty("org.osgi.service.http.port").value(String.valueOf(port));
    }

    protected static Option configZKDiscovery() {
        return newConfiguration("org.apache.aries.rsa.discovery.zookeeper") //
            .put("zookeeper.host", "127.0.0.1") //
            .put("zookeeper.port", ZK_PORT).asOption();
    }

    protected static Option configZKServer() {
        return composite(
                newConfiguration("org.apache.aries.rsa.discovery.zookeeper.server") //
                    .put("clientPort", ZK_PORT) //
                    .asOption(),
                systemProperty("zookeeper.admin.enableServer").value("false"));
    }

    protected static Option configFastBinPort(int port) {
        return newConfiguration("org.apache.aries.rsa.provider.fastbin") //
            .put("uri", "tcp://0.0.0.0:" + port) //
            .asOption();
    }

    protected static Option configFastBinFreePort() throws IOException {
        return configFastBinPort(getFreePort());
    }

}
