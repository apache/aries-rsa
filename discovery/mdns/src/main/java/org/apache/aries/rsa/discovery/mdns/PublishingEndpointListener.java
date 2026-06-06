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

import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.apache.aries.rsa.spi.discovery.LocalEndpointManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.jaxrs.whiteboard.annotations.RequireJaxrsWhiteboard;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for local {@link EndpointEvent}s using {@link EndpointEventListener}
 * and publishes changes to listeners using Server Sent Events (SSE)
 */
@RequireJaxrsWhiteboard
public class PublishingEndpointListener {

    private static final Logger LOG = LoggerFactory.getLogger(PublishingEndpointListener.class);

    private final EndpointDescriptionParser parser;

    private final LocalEndpointManager localEndpointManager;

    private final ServiceRegistration<?> resourceReg;

    private final Set<Subscription> listeners = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("serial")
    public PublishingEndpointListener(EndpointDescriptionParser parser, BundleContext context) {
        this.parser = parser;
        this.localEndpointManager = new LocalEndpointManager();
        localEndpointManager.setListener((event, filter) -> endpointUpdate(event.getEndpoint(), event.getType()));
        localEndpointManager.start(context, getClass().getName());
        resourceReg = context.registerService(PublishingEndpointListener.class, this,
            new Hashtable<>() {{put("osgi.jaxrs.resource", Boolean.TRUE);}});
    }

    @Deactivate
    public void stop() {
        localEndpointManager.stop();
        listeners.forEach(Subscription::close);
        resourceReg.unregister();
    }

    private void endpointUpdate(EndpointDescription ed, int type) {
        String id = ed.getId();
        switch(type) {
            case EndpointEvent.ADDED:
            case EndpointEvent.MODIFIED:
                String data = toEndpointData(ed);
                listeners.forEach(s -> s.update(data));
                break;
            case EndpointEvent.MODIFIED_ENDMATCH:
            case EndpointEvent.REMOVED:
                listeners.forEach(s -> s.revoke(id));
                break;
            default:
                LOG.error("Unknown event type {} for endpoint {}", type, ed);
        }
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

        localEndpointManager.getEndpoints().stream()
            .map(this::toEndpointData)
            .forEach(subscription::update);
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
            if (t != null) {
                LOG.error("Failed to send endpoint message, closing");
                listeners.remove(this);
                eventSink.close();
            }
        }
    }
}
