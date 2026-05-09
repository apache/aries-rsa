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

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.apache.aries.rsa.discovery.mdns.PublishingEndpointListener.Subscription.ENDPOINT_REVOKED;
import static org.apache.aries.rsa.discovery.mdns.PublishingEndpointListener.Subscription.ENDPOINT_UPDATED;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.osgi.framework.Constants;
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
public class InterestManager {
    private static final Logger LOG = LoggerFactory.getLogger(InterestManager.class);

    private final ConcurrentMap<Long, Interest> interests = new ConcurrentHashMap<>();

    private final SseEventSourceFactory eventSourceFactory;
    
    private final EndpointDescriptionParser parser;
    
    private final Client client;
    
    private final ConcurrentMap<String, Set<EndpointDescription>> endpointsBySource = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<String, SseEventSource> streams = new ConcurrentHashMap<>();
    
    public InterestManager(SseEventSourceFactory factory, EndpointDescriptionParser parser, Client client) {
        
        this.eventSourceFactory = factory;
        this.parser = parser;
        this.client = client;
        
    }

    public void deactivate() {
       
        streams.values().forEach(SseEventSource::close);
        streams.clear();
        
        interests.clear();
    }

    public void remoteAdded(String uri) {
        if(streams.containsKey(uri)) {
            return;
        }
        
        if(LOG.isInfoEnabled()) {
            LOG.info("Discovered a remote at {}", uri);
        }
        
        SseEventSource sse = eventSourceFactory.newBuilder(client.target(uri)).build();
        sse.register(i -> onEndpointEvent(uri, i), t -> lostRemoteStream(uri, t), () -> lostRemoteStream(uri, null));
        streams.put(uri, sse);
        sse.open();
    }
    
    public void remoteRemoved(String uri) {
        if(LOG.isInfoEnabled()) {
            LOG.info("Remote at {} is no longer present", uri);
        }
        
        SseEventSource sseEventSource = streams.remove(uri);
        if(sseEventSource != null) {
            sseEventSource.close();
        }
    }
    
    private void onEndpointEvent(String source, InboundSseEvent event) {
        String name = event.getName();
        
        if(LOG.isDebugEnabled()) {
            LOG.debug("Received a {} notification from {}", name, source);
        }
        
        if(ENDPOINT_UPDATED.equals(name)) {
            EndpointDescription ed = parser.readEndpoint(event.readData(InputStream.class));
            endpointsBySource.compute(source, (a,b) -> {
                return b == null ? singleton(ed) : concat(b.stream(), Stream.of(ed)).collect(toSet());
            });
            interests.values().forEach(i -> i.endpointChanged(ed));
        } else if (ENDPOINT_REVOKED.equals(name)) {
            String id = event.readData();
            endpointsBySource.compute(source, (a,b) -> {
                if(b == null) {
                    return null;
                } else {
                    Set<EndpointDescription> set = b.stream().filter(ed -> !ed.getId().equals(id)).collect(toSet());
                    return set.isEmpty() ? null : set;
                }
            });
            interests.values().forEach(i -> i.endpointRemoved(id));
        }
    }
    
    private void lostRemoteStream(String source, Throwable t) {
        
        if(t != null) {
            if(LOG.isWarnEnabled()) {
                LOG.warn("The remote {} had a failure", source, t);
            }
        } else {
            if(LOG.isInfoEnabled()) {
                LOG.info("The remote {} has disconnected", source);
            }
        }
        
        Set<EndpointDescription> remove = endpointsBySource.remove(source);
        if(remove != null) {
            remove.forEach(ed -> interests.values().forEach(i -> i.endpointRemoved(ed.getId())));
        }
    }

    private Long getServiceId(Map<String, Object> props) {
        return (Long) props.get(Constants.SERVICE_ID);
    }

    public void addInterest(EndpointEventListener epListener, Map<String, Object> props) {

        Long id = getServiceId(props);

        if(LOG.isInfoEnabled()) {
            LOG.info("Service {} has registered an interest in endpoint events", id);
        }

        Interest interest = new Interest(id, epListener, props);

        interests.put(id, interest);
        endpointsBySource.values().stream()
            .flatMap(Set::stream)
            .forEach(interest::endpointChanged);
    }

    public void updateInterest(Map<String, Object> props) {

        Long id = getServiceId(props);

        if(LOG.isInfoEnabled()) {
            LOG.info("Service {} has changed its interest in endpoint events", id);
        }

        interests.get(id).update(props);
    }

    public void removeInterest(Map<String, Object> props) {
        interests.remove(getServiceId(props));
    }
}
