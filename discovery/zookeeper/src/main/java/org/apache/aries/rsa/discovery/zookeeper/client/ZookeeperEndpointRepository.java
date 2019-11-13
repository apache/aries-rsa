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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Is called by PublishingEndpointListener with local Endpoint changes and forward the changes to Zookeeper. 
 */
@Component(service = ZookeeperEndpointRepository.class)
public class ZookeeperEndpointRepository {
    public static final String PATH_PREFIX = "/osgi/service_registry";
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperEndpointRepository.class);
    private final Map<Integer, String> typeNames = new HashMap<>();
    
    @Reference
    private ZooKeeper zk;
    
    @Reference
    private EndpointDescriptionParser parser;
    
    public ZookeeperEndpointRepository() {
        typeNames.put(EndpointEvent.ADDED, "added");
        typeNames.put(EndpointEvent.MODIFIED, "modified");
        typeNames.put(EndpointEvent.MODIFIED_ENDMATCH, "modified");
        typeNames.put(EndpointEvent.REMOVED, "removed");
    }
    
    public ZookeeperEndpointRepository(ZooKeeper zk, EndpointDescriptionParser parser) {
        this();
        this.zk = zk;
        this.parser = parser;
    }

    @Activate 
    public void activate() {
        try {
            createPath(PATH_PREFIX);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create base path", e);
        }
    }
    
    public ZookeeperEndpointListener createListener(EndpointEventListener listener) {
        return new ZookeeperEndpointListener(zk, parser, listener);
    }

    public void endpointChanged(EndpointEvent event) {
        try {
            EndpointDescription endpoint = event.getEndpoint();
            switch (event.getType()) {
            case EndpointEvent.ADDED:
                add(endpoint);
                break;
            case EndpointEvent.MODIFIED:
            case EndpointEvent.MODIFIED_ENDMATCH:
                modify(endpoint);
                break;
            case EndpointEvent.REMOVED:
                remove(endpoint);
                break;
            default:
                break;
            }
        } catch (Exception e) {
            logException(typeNames.get(event.getType()), event.getEndpoint(), e);
        }
    }
    
    private void logException(String operation, EndpointDescription endpoint, Exception ex) {
        String msg = String.format("Exception during %s of endpoint %s", operation, endpoint.getId());
        LOG.error(msg, ex);
    }


    private void add(EndpointDescription endpoint) throws KeeperException, InterruptedException  {
        String path = getZooKeeperPath(endpoint.getId());
        LOG.info("Exporting path: {}, Endpoint: {}", path, endpoint);
        createBasePath();
        byte[] data = getData(endpoint);
        createEphemeralNode(path, data);
    }

    private void modify(EndpointDescription endpoint) throws KeeperException, InterruptedException {
        String path = getZooKeeperPath(endpoint.getId());
        LOG.info("Changing endpoint in zookeeper. Endpoint: {}, Path: {}", endpoint, path);
        createBasePath();
        zk.setData(path, getData(endpoint), -1);
    }
    
    private void remove(EndpointDescription endpoint) throws KeeperException, InterruptedException {
        String path = getZooKeeperPath(endpoint.getId());
        LOG.info("Removing endpoint in zookeeper. Endpoint: {}, Path: {}", endpoint, path);
        zk.delete(path, -1);
    }
    
    private boolean notEmpty(String part) {
        return part != null && !part.isEmpty();
    }

    static String getZooKeeperPath(String name) {
        String escaped = name.replace('/', '#');
        return name == null || name.isEmpty() ? PATH_PREFIX : PATH_PREFIX + '/' + escaped;
    }

    private void createBasePath() throws KeeperException, InterruptedException {
        String path = ZookeeperEndpointRepository.getZooKeeperPath("");
        createPath(path);
    }

    private byte[] getData(EndpointDescription epd) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        parser.writeEndpoint(epd, bos);
        return bos.toByteArray();
    }
    
    private void createEphemeralNode(String fullPath, byte[] data) throws KeeperException, InterruptedException {
        try {
            zk.create(fullPath, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (NodeExistsException nee) {
            // this sometimes happens after a ZooKeeper node dies and the ephemeral node
            // that belonged to the old session was not yet deleted. We need to make our
            // session the owner of the node so it won't get deleted automatically -
            // we do this by deleting and recreating it ourselves.
            LOG.info("node for endpoint already exists, recreating: {}", fullPath);
            try {
                zk.delete(fullPath, -1);
            } catch (NoNodeException nne) {
                // it's a race condition, but as long as it got deleted - it's ok
            }
            zk.create(fullPath, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }
    }
    
    private void createPath(String path) throws KeeperException, InterruptedException {
        List<String> parts = asList(path.split("/")).stream()
                .filter(this::notEmpty)
                .collect(toList());
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            current.append('/');
            current.append(part);
            createNode(current.toString());
        }
    }

    private void createNode(String path) throws KeeperException, InterruptedException {
        try {
            if (zk.exists(path, false) == null) {
                zk.create(path, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (NodeExistsException nee) {
            // it's not the first node with this path to ever exist - that's normal
        }
    }

}
