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
package org.apache.aries.rsa.discovery.mdns;

import static org.apache.aries.rsa.discovery.mdns.PublishingEndpointListener.Subscription.ENDPOINT_REVOKED;
import static org.apache.aries.rsa.discovery.mdns.PublishingEndpointListener.Subscription.ENDPOINT_UPDATED;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.ws.rs.client.Client;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.osgi.service.jaxrs.client.SseEventSourceFactory;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the {@link EndpointEventListener}s and the scopes they are interested in.
 * Establishes SSE event sources to be called back on all changes in the remote targets.
 * Events are then forwarded to all interested {@link EndpointEventListener}s.
 */
public class InterestManager extends org.apache.aries.rsa.spi.discovery.InterestManager {
    private static final Logger LOG = LoggerFactory.getLogger(InterestManager.class);

    private final SseEventSourceFactory eventSourceFactory;

    private final EndpointDescriptionParser parser;

    private final Client client;

    private final ConcurrentMap<String, SseEventSource> streams = new ConcurrentHashMap<>();

    public InterestManager(SseEventSourceFactory factory, EndpointDescriptionParser parser, Client client) {
        this.eventSourceFactory = factory;
        this.parser = parser;
        this.client = client;
    }

    @Override
    public void stop() {
        super.stop();
        streams.values().forEach(SseEventSource::close);
        streams.clear();
    }

    public void remoteAdded(String uri) {
        if (streams.containsKey(uri)) {
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Discovered a remote at {}", uri);
        }

        SseEventSource sse = eventSourceFactory.newBuilder(client.target(uri)).build();
        sse.register(i -> onEndpointEvent(uri, i), t -> lostRemoteStream(uri, t), () -> lostRemoteStream(uri, null));
        streams.put(uri, sse);
        sse.open();
    }

    public void remoteRemoved(String uri) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Remote at {} is no longer present", uri);
        }

        SseEventSource sseEventSource = streams.remove(uri);
        if (sseEventSource != null) {
            sseEventSource.close();
        }
        removeSource(uri);
    }

    private void onEndpointEvent(String source, InboundSseEvent event) {
        String name = event.getName();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Received a {} notification from {}", name, source);
        }

        if (ENDPOINT_UPDATED.equals(name)) {
            EndpointDescription ed = parser.readEndpoint(event.readData(InputStream.class));
            addEndpoint(source, ed);
        } else if (ENDPOINT_REVOKED.equals(name)) {
            String id = event.readData();
            removeEndpoint(source, id);
        }
    }

    private void lostRemoteStream(String source, Throwable t) {
        if (t != null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("The remote {} had a failure", source, t);
            }
        } else {
            if (LOG.isInfoEnabled()) {
                LOG.info("The remote {} has disconnected", source);
            }
        }

        removeSource(source);
    }
}
