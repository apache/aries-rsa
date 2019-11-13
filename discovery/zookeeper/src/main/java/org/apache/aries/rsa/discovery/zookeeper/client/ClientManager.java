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

import static java.util.concurrent.CompletableFuture.runAsync;

import java.io.IOException;
import java.util.Hashtable;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.ZooTrace;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a Zookeeper client from a config. The Zookeeper service is published when the connection
 * has been established and will be unpublished when the connection goes away.
 */
@Component(//
        service = ClientManager.class,
        immediate = true,
        configurationPid = ClientManager.DISCOVERY_ZOOKEEPER_ID, //
        configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class ClientManager implements Watcher {
    public static final String DISCOVERY_ZOOKEEPER_ID = "org.apache.aries.rsa.discovery.zookeeper";
    private static final Logger LOG = LoggerFactory.getLogger(ClientManager.class);

    private ZooKeeper zkClient;
    private DiscoveryConfig config;
    private volatile ServiceRegistration<ZooKeeper> reg;
    private BundleContext context;

    @Activate
    public synchronized void activate(final DiscoveryConfig config, final BundleContext context) {
        this.config = config;
        this.context = context;
        LOG.debug("Received configuration update for Zookeeper Discovery: {}", config);
        startClient();
    }

    private void startClient() {
        try {
            this.zkClient = createClient(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected ZooKeeper createClient(DiscoveryConfig config) throws IOException {
        LOG.info("ZooKeeper discovery connecting to {}:{} with timeout {}", config.zookeeper_host(), config.zookeeper_port(), config.zookeeper_timeout());
        return new ZooKeeper(config.zookeeper_host() + ":" + config.zookeeper_port(), config.zookeeper_timeout(), this);
    }

    @Deactivate
    public synchronized void stop() {
        // Load ZooTrace class early to avoid ClassNotFoundException on shutdown
        ZooTrace.getTextTraceLevel();
        
        if (reg != null) {
            reg.unregister();
        }
        runAsync(this::closeClient);
    }

    private void closeClient() {
        try {
            zkClient.close();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /* Callback for ZooKeeper */
    public void process(WatchedEvent event) {
        LOG.debug("Got ZooKeeper event " + event);

        if (event.getState() == KeeperState.SyncConnected) {
            LOG.info("Connection to ZooKeeper established. Publishing Zookeeper service");
            this.reg = context.registerService(ZooKeeper.class, zkClient, new Hashtable<String, String>());
        }

        if (event.getState() == KeeperState.Expired) {
            LOG.info("Connection to ZooKeeper expired. Trying to create a new connection");
            stop();
            startClient();
        }
    }

    @ObjectClassDefinition(name = "Zookeeper discovery config")
    public @interface DiscoveryConfig {
        String zookeeper_host() default "localhost";
        String zookeeper_port() default "2181";
        int zookeeper_timeout() default 3000;
    }
}
