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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig.ConfigException;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component( //
        configurationPolicy = ConfigurationPolicy.REQUIRE, //
        configurationPid = "org.apache.aries.rsa.discovery.zookeeper.server" //
)
public class ZookeeperStarter {

    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperStarter.class);

    protected ZookeeperServer server;
    private Thread zkMainThread;

    @Activate
    public synchronized void activate(BundleContext context, Map<String, String> config) throws ConfigurationException {
        LOG.info("Activating zookeeper server with config: {}", config);
        try {
            QuorumPeerConfig peerConfig = parseConfig(config, context);
            startFromConfig(peerConfig);
        } catch (Exception th) {
            LOG.warn("Problem applying configuration update: " + config, th);
        }
    }

    @Deactivate
    public synchronized void deactivate() {
        if (server == null) {
            return;
        }
        LOG.info("Shutting down ZooKeeper server");
        try {
            server.shutdown();
            if (zkMainThread != null) {
                zkMainThread.join();
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
    }

    protected ZookeeperServer createServer(final QuorumPeerConfig config) {
        int numServers = config.getServers().size();
        return numServers > 1 ? new MyQuorumPeerMain(config) : new MyZooKeeperServerMain(config);
    }

    private void startFromConfig(final QuorumPeerConfig config) {
        this.server = createServer(config);
        zkMainThread = new Thread(new Runnable() {
            public void run() {
                try {
                    server.startup();
                } catch (Throwable e) {
                    LOG.error("Problem running ZooKeeper server.", e);
                }
            }
        });
        zkMainThread.start();
    }

    private QuorumPeerConfig parseConfig(Map<String, ?> config, BundleContext context) throws IOException, ConfigException {
        Properties props = copyWithoutEmptyValues(config); // to avoid NumberFormatExceptions
        String dataDir = new File(context.getDataFile(""), "zkdata").getCanonicalPath();
        props.putIfAbsent("dataDir", dataDir);
        props.putIfAbsent("tickTime", "2000");
        props.putIfAbsent("initLimit", "10");
        props.putIfAbsent("syncLimit", "5");
        props.putIfAbsent("clientPort", "2181");
        QuorumPeerConfig qconf = new QuorumPeerConfig();
        qconf.parseProperties(props);
        return qconf;
    }

    /**
     * Remove entries whose values are empty from the given dictionary.
     *
     * @param dict a dictionary
     * @return 
     */
    private static Properties copyWithoutEmptyValues(Map<String, ?> dict) {
        Properties props = new Properties();
        for (String key : dict.keySet()) {
            Object value = dict.get(key);
            if (!(value instanceof String && "".equals(value))) {
                props.put(key, value);
            }
        }
        return props;
    }

}
