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

import static org.osgi.framework.Constants.FRAMEWORK_UUID;
import static org.osgi.service.jaxrs.runtime.JaxrsServiceRuntimeConstants.JAX_RS_SERVICE_ENDPOINT;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.jaxrs.client.SseEventSourceFactory;
import org.osgi.service.jaxrs.runtime.JaxrsServiceRuntime;
import org.osgi.service.jaxrs.runtime.dto.RuntimeDTO;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
@org.apache.aries.rsa.annotations.RSADiscoveryProvider(protocols = "aries.mdns")
@Component
public class MdnsDiscovery {

    private static final String _ARIES_DISCOVERY_HTTP_TCP_LOCAL = "_aries-discovery._tcp.local.";

    private static final Logger LOG = LoggerFactory.getLogger(MdnsDiscovery.class);
    
    private final Client client;
    
    private final String fwUuid;
    
    private final InterestManager interestManager;
    
    private final PublishingEndpointListener publishingListener;
    
    private JaxrsServiceRuntime runtime;
    
    private JmDNS jmdns;


    @Activate
    public MdnsDiscovery(BundleContext ctx, @Reference SseEventSourceFactory eventSourceFactory,
            @Reference ClientBuilder clientBuilder, @Reference EndpointDescriptionParser parser) {
        this.client = clientBuilder.build();
        this.interestManager = new InterestManager(eventSourceFactory, parser, client);
        fwUuid = ctx.getProperty(FRAMEWORK_UUID);
        this.publishingListener = new PublishingEndpointListener(parser, ctx, fwUuid);
    }
    
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindEndpointEventListener(EndpointEventListener epListener, Map<String, Object> props) {
        interestManager.bindEndpointEventListener(epListener, props);
    }

    public void updatedEndpointEventListener(Map<String, Object> props) {
        interestManager.updatedEndpointEventListener(props);
    }

    public void unbindEndpointEventListener(Map<String, Object> props) {
        interestManager.unbindEndpointEventListener(props);
    }

    @Reference(policy = ReferencePolicy.DYNAMIC)
    public void bindJaxrsServiceRuntime(JaxrsServiceRuntime runtime) {
        updateAndRegister(runtime);
    }

    public void updatedJaxrsServiceRuntime(JaxrsServiceRuntime runtime) {
        updateAndRegister(runtime);
    }

    public void unbindJaxrsServiceRuntime(JaxrsServiceRuntime runtime) {
        JmDNS jmdns = null;
        synchronized (this) {
            if(runtime == this.runtime) {
                jmdns = this.jmdns;
                this.runtime = null;
            }
        }
        
        if(jmdns != null) {
            jmdns.unregisterAllServices();
        }
    }

    private void updateAndRegister(JaxrsServiceRuntime runtime) {
        JmDNS jmdns;
        synchronized (this) {
            this.runtime = runtime;
            jmdns = this.jmdns;
        }
        
        if(jmdns != null) {
            RuntimeDTO runtimeDTO = runtime.getRuntimeDTO();
            List<String> uris = StringPlus.normalize(runtimeDTO.serviceDTO.properties.get(JAX_RS_SERVICE_ENDPOINT));
            
            if(uris == null || uris.isEmpty()) {
                LOG.warn("Unable to advertise discovery as there are no endpoint URIs");
                return;
            }
            
            String base = runtimeDTO.defaultApplication.base;
            if(base == null) {
                base = "";
            }
            
            base += "/aries/rsa/discovery";
            
            URI uri = uris.stream()
                .filter(s -> s.matches(".*(?:[0-9]{1,3}\\.){3}[0-9]{1,3}.*"))
                .findFirst()
                .map(URI::create)
                .orElseGet(() -> URI.create(uris.get(0)));
            
            Map<String, Object> props = new HashMap<>();
            props.put("scheme", uri.getScheme() == null ? "" : uri.getScheme());
            props.put("path", uri.getPath() == null ? base : uri.getPath() + base);
            props.put("frameworkUuid", fwUuid);
            
            ServiceInfo info = ServiceInfo.create(_ARIES_DISCOVERY_HTTP_TCP_LOCAL, fwUuid, uri.getPort(), 0, 0, props);
            
            try {
                jmdns.registerService(info);
            } catch (IOException ioe) {
                LOG.error("Unable to advertise discovery", ioe);
            }
        }
    }
    
    public static @interface Config {
        public String bind_address();
    }
    
    @Activate
    public void start(Config config) throws UnknownHostException, IOException {

        String bind = config.bind_address();
        
        JmDNS jmdns = JmDNS.create(bind == null ? null : InetAddress.getByName(bind));
        
        JaxrsServiceRuntime runtime;
        synchronized (this) {
            this.jmdns = jmdns;
            runtime = this.runtime;
        }
        
        if(runtime != null) {
            updateAndRegister(runtime);
        }
    
        // Add a service listener
        jmdns.addServiceListener(_ARIES_DISCOVERY_HTTP_TCP_LOCAL, new MdnsListener());
        
    }
    
    @Deactivate
    public void stop () {
        try {
            jmdns.close();
        } catch (IOException e) {
            LOG.warn("An exception occurred closing the mdns discovery");
        }
        
        interestManager.deactivate();
        publishingListener.stop();
    }

    private class MdnsListener implements ServiceListener {
        
        private final ConcurrentMap<String, String> namesToUris = new ConcurrentHashMap<>();
        
        @Override
        public void serviceAdded(ServiceEvent event) {
        }
        
        @Override
        public void serviceRemoved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            if(info != null) {
                String removed = namesToUris.remove(info.getKey());
                if(removed != null) {
                    interestManager.remoteRemoved(removed);
                }
            }
        }
        
        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            
            String infoUuid = info.getPropertyString("frameworkUuid");
            
            if(infoUuid == null || infoUuid.equals(fwUuid)) {
                // Ignore until we can see if this is for our own endpoint
                return;
            }
            
            String scheme = info.getPropertyString("scheme");
            if(scheme == null) {
                scheme = "http";
            }
            
            String path = info.getPropertyString("path");
            if(path == null) {
                // Not a complete record yet
                return;
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            int port = info.getPort();
            if(port == -1) {
                switch(scheme) {
                    case "http":
                        port = 80;
                        break;
                    case "https":
                        port = 443;
                        break;
                    default:
                        LOG.error("Unknown URI scheme advertised {} by framework {} on host {}", 
                                scheme, info.getName(), info.getDomain());
                }
            }
            
            String address = info.getInetAddresses()[0].getHostAddress();
            
            String uri = String.format("%s://%s:%d/%s", scheme, address, port, path);
            
            LOG.info("Discovered remote at {}", uri);
            
            namesToUris.put(info.getKey(), uri);
            
            interestManager.remoteAdded(uri);
        }
    }
}
