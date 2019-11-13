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
package org.apache.aries.rsa.provider.tcp;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.aries.rsa.annotations.RSADistributionProvider;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.spi.IntentUnsatisfiedException;
import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
@RSADistributionProvider(configs="aries.tcp")
@Component(property = { //
        RemoteConstants.REMOTE_INTENTS_SUPPORTED + "=osgi.basic,osgi.async",
        RemoteConstants.REMOTE_CONFIGS_SUPPORTED + "=" + TCPProvider.TCP_CONFIG_TYPE //
})
public class TCPProvider implements DistributionProvider {
    static final String TCP_CONFIG_TYPE = "aries.tcp";
    private static final String[] SUPPORTED_INTENTS = { "osgi.basic", "osgi.async"};
    
    private Logger logger = LoggerFactory.getLogger(TCPProvider.class);

    @Override
    public String[] getSupportedTypes() {
        return new String[] {TCP_CONFIG_TYPE};
    }

    @Override
    public Endpoint exportService(Object serviceO, 
                                  BundleContext serviceContext,
                                  Map<String, Object> effectiveProperties,
                                  Class[] exportedInterfaces) {

        effectiveProperties.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, getSupportedTypes());
        Set<String> intents = getCombinedIntents(effectiveProperties);
        intents.removeAll(Arrays.asList(SUPPORTED_INTENTS));
        if (!intents.isEmpty()) {
            logger.warn("Unsupported intents found: {}. Not exporting service", intents);
            return null;
        }
        return new TcpEndpoint(serviceO, effectiveProperties);
    }

    private Set<String> getCombinedIntents(Map<String, Object> effectiveProperties) {
        Set<String> combinedIntents = new HashSet<>();
        List<String> intents = StringPlus.normalize(effectiveProperties.get(RemoteConstants.SERVICE_EXPORTED_INTENTS));
        if (intents != null) {
            combinedIntents.addAll(intents);
        }
        List<String> intentsExtra = StringPlus.normalize(effectiveProperties.get(RemoteConstants.SERVICE_EXPORTED_INTENTS_EXTRA));
        if (intentsExtra != null) {
            combinedIntents.addAll(intentsExtra);
        }
        return combinedIntents;
    }

    @Override
    public Object importEndpoint(ClassLoader cl, 
                                 BundleContext consumerContext, 
                                 Class[] interfaces,
                                 EndpointDescription endpoint)
        throws IntentUnsatisfiedException {
        try {
            URI address = new URI(endpoint.getId());
            Integer timeout = new EndpointPropertiesParser(endpoint).getTimeoutMillis();
            InvocationHandler handler = new TcpInvocationHandler(cl, address.getHost(), address.getPort(), timeout);
            return Proxy.newProxyInstance(cl, interfaces, handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
