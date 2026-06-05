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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.util.List;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.apache.aries.rsa.spi.discovery.InterestManager;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.SessionExpiredException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to endpoint changes in Zookeeper and forwards changes in Endpoints to InterestManager.
 */
public class ZookeeperEndpointListener implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperEndpointListener.class);

    private ZooKeeper zk;
    private EndpointDescriptionParser parser;
    private InterestManager interestManager;
    private volatile boolean closed;

    ZookeeperEndpointListener(ZooKeeper zk, EndpointDescriptionParser parser, InterestManager interestManager) {
        this.zk = zk;
        this.parser = parser;
        this.interestManager = interestManager;
        watchRecursive(ZookeeperEndpointRepository.PATH_PREFIX);
    }

    @Override
    public void close() {
        closed = true;
    }

    private void process(WatchedEvent event) {
        if (closed) {
            return;
        }
        String path = event.getPath();
        LOG.info("Received event {}", event);
        switch (event.getType()) {
        case NodeCreated:
        case NodeDataChanged:
        case NodeChildrenChanged:
            watchRecursive(path);
            break;
        case NodeDeleted:
            onRemoved(path);
            break;
        default:
            break;
        }
    }

    private void watchRecursive(String path) {
        LOG.info("Watching {}", path);
        try {
            EndpointDescription endpoint = read(path);
            if (endpoint != null) {
                onChanged(path, endpoint);
            }
            List<String> children = zk.getChildren(path, this::process);
            if (children == null) {
                return;
            }
            for (String child : children) {
                String childPath = (path.endsWith("/") ? path : path + "/") + child;
                watchRecursive(childPath);
            }
        } catch (NoNodeException | SessionExpiredException | ConnectionLossException e) {
            // NoNodeException happens when a node was removed
            LOG.debug(e.getMessage(), e);
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
    }

    private void onChanged(String path, EndpointDescription endpoint) {
        interestManager.addEndpoint(path, endpoint);
    }

    private void onRemoved(String path) {
        interestManager.removeSource(path);
    }

    private EndpointDescription read(String path) throws KeeperException, InterruptedException {
        Stat stat = new Stat();
        byte[] data = zk.getData(path, this::process, stat);
        if (data == null || data.length == 0) {
            return null;
        } else {
            return parser.readEndpoint(new ByteArrayInputStream(data));
        }
    }
}
