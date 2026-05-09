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
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.jaxrs.whiteboard.annotations.RequireJaxrsWhiteboard;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for local {@link EndpointEvent}s using {@link EndpointEventListener}
 * and publishes changes to listeners using Server Sent Events (SSE)
 */
@RequireJaxrsWhiteboard
public class PublishingEndpointListener {

    private static final Logger LOG = LoggerFactory.getLogger(MdnsDiscovery.class);
    
    private final String uuid;

    private final EndpointDescriptionParser parser;
    
    private final ServiceRegistration<?> listenerReg;
    private final ServiceRegistration<?> resourceReg;
    
    private final ConcurrentMap<String, SponsoredEndpoint> localEndpoints = new ConcurrentHashMap<>();
    
    private final Set<Subscription> listeners = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("serial")
    public PublishingEndpointListener(EndpointDescriptionParser parser, BundleContext bctx, String uuid) {
        this.parser = parser;
        this.uuid = uuid;
        String[] ifAr = { EndpointEventListener.class.getName() };
        Dictionary<String, Object> props = serviceProperties(uuid);
        listenerReg = bctx.registerService(ifAr, new ListenerFactory(), props);
        resourceReg = bctx.registerService(PublishingEndpointListener.class, this, 
                new Hashtable<>() {{put("osgi.jaxrs.resource", Boolean.TRUE);}});
    }

    @Deactivate
    public void stop() {
        listenerReg.unregister();
        listeners.forEach(Subscription::close);
        resourceReg.unregister();
    }

    private void endpointUpdate(Long bundleId, EndpointDescription ed, int type) {
        String edFwUuid = ed.getFrameworkUUID();
        if(edFwUuid == null || !edFwUuid.equals(uuid)) {
            LOG.warn("This listener has been called with an endpoint {} for a remote framework {}", ed.getId(), edFwUuid);
            return;
        }
        String id = ed.getId();
        switch(type) {
            case EndpointEvent.ADDED:
            case EndpointEvent.MODIFIED:
                localEndpoints.compute(id, (k,v) -> new SponsoredEndpoint(ed,
                    v == null
                    ? singleton(bundleId)
                    : concat(v.sponsors.stream(), Stream.of(bundleId)).collect(toSet())));
                String data = toEndpointData(ed);
                listeners.forEach(s -> s.update(data));
                break;
            case EndpointEvent.MODIFIED_ENDMATCH:
            case EndpointEvent.REMOVED:
                boolean act = localEndpoints.compute(id, (k,v) -> {
                    if(v == null) {
                        return null;
                    } else {
                      Set<Long> updated = v.sponsors.stream().filter(l -> !bundleId.equals(l)).collect(toSet());
                      return updated.isEmpty() ? null : new SponsoredEndpoint(v.ed, updated);
                    }
                }) == null;

                if(act) {
                    listeners.forEach(s -> s.revoke(id));
                }
                break;
            default:
                LOG.error("Unknown event type {} for endpoint {}", type, ed);
        }
    }

    private Dictionary<String, Object> serviceProperties(String uuid) {
        String scope = String.format("(&(%s=*)(%s=%s))", Constants.OBJECTCLASS,
                        RemoteConstants.ENDPOINT_FRAMEWORK_UUID, uuid);
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, scope);
        return props;
    }

    private String toEndpointData(EndpointDescription ed) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            parser.writeEndpoint(ed, baos);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8).replace("\n", "").replace("\r", "");
        } catch (Exception e) {
            LOG.error("Unable to serialize the endpoint {}", ed, e);
            throw new RuntimeException(e);
        }
    }
    
    @GET
    @Produces(SERVER_SENT_EVENTS)
    @Path("aries/rsa/discovery")
    public void listen(@Context Sse sse, @Context SseEventSink sink) {
        Subscription subscription = new Subscription(sse, sink);
        listeners.add(subscription);
        
        localEndpoints.values().stream()
            .map(s -> toEndpointData(s.ed))
            .forEach(subscription::update);
    }

    private class ListenerFactory implements ServiceFactory<PerClientEndpointEventListener> {

        @Override
        public PerClientEndpointEventListener getService(Bundle bundle,
                ServiceRegistration<PerClientEndpointEventListener> registration) {
            return new PerClientEndpointEventListener(bundle.getBundleId());
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration<PerClientEndpointEventListener> registration,
                PerClientEndpointEventListener service) {
            Long bundleId = service.bundleId;
            localEndpoints.values().stream()
                .filter(s -> s.sponsors.contains(bundleId))
                .forEach(s -> endpointUpdate(bundleId, s.ed, EndpointEvent.REMOVED));
        }
        
    }
    
    private class PerClientEndpointEventListener implements EndpointEventListener {
        
        private final Long bundleId;
        
        public PerClientEndpointEventListener(Long bundleId) {
            super();
            this.bundleId = bundleId;
        }

        @Override
        public void endpointChanged(EndpointEvent event, String filter) {
            endpointUpdate(bundleId, event.getEndpoint(), event.getType());
        }
    }

    class Subscription {
        
        static final String ENDPOINT_UPDATED = "UPDATED";
        static final String ENDPOINT_REVOKED = "REVOKED";
        
        Sse sse;
        SseEventSink eventSink;
        
        public Subscription(Sse sse, SseEventSink eventSink) {
            this.sse = sse;
            this.eventSink = eventSink;
        }

        public void update(String endpointData) {
            eventSink.send(sse.newEvent(ENDPOINT_UPDATED, endpointData))
                .whenComplete(this::sendFailure);
        }
        
        public void revoke(String endpointId) {
            eventSink.send(sse.newEvent(ENDPOINT_REVOKED, endpointId))
                .whenComplete(this::sendFailure);
        }
        
        public void close() {
            eventSink.close();
            listeners.remove(this);
        }
        
        private void sendFailure(Object o, Throwable t) {
            if(t != null) {
                LOG.error("Failed to send endpoint message, closing");
                listeners.remove(this);
                eventSink.close();
            }
        }
    }
    
    private static class SponsoredEndpoint {
        private final EndpointDescription ed;
        private final Set<Long> sponsors;
        
        public SponsoredEndpoint(EndpointDescription ed, Set<Long> sponsors) {
            this.ed = ed;
            this.sponsors = sponsors;
        }
    }
}
