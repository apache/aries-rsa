/**
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
package org.apache.aries.rsa.provider.fastbin;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.provider.fastbin.io.ClientInvoker;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.util.UuidGenerator;
import org.apache.aries.rsa.spi.DistributionProvider;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator extends BaseActivator implements ManagedService {

	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);
    static Activator INSTANCE;
    FastBinProvider provider;
    ClientInvoker client;
    ServerInvoker server;
    

    @Override
    protected void doOpen() throws Exception {
        manage("org.apache.aries.rsa.provider.fastbin");
    }

    @Override
    protected void doStart() throws Exception {
        INSTANCE = this;
        String uri = getString("uri", "tcp://0.0.0.0:2543");
        LOG.info("Binding Fastbin Server to {}", uri);
        String exportedAddress = getString("exportedAddress", null);
        if (exportedAddress == null) {
            exportedAddress = UuidGenerator.getHostName();
        }
        long timeout = getLong("timeout", TimeUnit.MINUTES.toMillis(5));
        provider = new FastBinProvider(uri, exportedAddress, timeout);
        client = provider.getClient();
        server = provider.getServer();
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(RemoteConstants.REMOTE_INTENTS_SUPPORTED, new String[]{});
        props.put(RemoteConstants.REMOTE_CONFIGS_SUPPORTED, provider.getSupportedTypes());
        register(DistributionProvider.class, provider, props);
    }

    @Override
    protected void doStop() {
        super.doStop();
        if (provider != null) {
            try {
                provider.close();
            } finally {
                provider = null;
            }
        }
    }

    public ClientInvoker getClient() {
        return client;
    }

    public ServerInvoker getServer() {
        return server;
    }

    public static Activator getInstance() {
        return INSTANCE;
    }

}
