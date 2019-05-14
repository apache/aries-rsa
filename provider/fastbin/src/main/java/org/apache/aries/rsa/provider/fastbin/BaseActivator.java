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

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class BaseActivator implements BundleActivator, Runnable {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected BundleContext bundleContext;

    protected ExecutorService executor = new ThreadPoolExecutor(0, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>());

    private long schedulerStopTimeout = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS);

    private List<ServiceRegistration> registrations;
    private ServiceRegistration managedServiceRegistration;
    private Dictionary<String, ?> configuration;

    public long getSchedulerStopTimeout() {
        return schedulerStopTimeout;
    }

    public void setSchedulerStopTimeout(long schedulerStopTimeout) {
        this.schedulerStopTimeout = schedulerStopTimeout;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        bundleContext = context;
        synchronized (this) {
            doOpen();
        }
        // if it's a managed service we'll get a configuration update
        // that will start it, otherwise we do it here manually
        if (managedServiceRegistration == null) {
            reconfigure();
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        synchronized (this) {
            doClose();
        }
        executor.shutdown();
        executor.awaitTermination(schedulerStopTimeout, TimeUnit.MILLISECONDS);
        synchronized (this) {
            doStop();
        }
    }

    protected void doOpen() throws Exception {
        URL data = bundleContext.getBundle().getResource("OSGI-INF/karaf-tracker/" + getClass().getName());
        if (data != null) {
            Properties props = new Properties();
            try (InputStream is = data.openStream()) {
                props.load(is);
            }
            for (String key : props.stringPropertyNames()) {
                if ("pid".equals(key)) {
                    manage(props.getProperty(key));
                }
            }
        }
    }

    protected void doClose() {
        if (managedServiceRegistration != null) {
            managedServiceRegistration.unregister();
        }
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() {
        if (registrations != null) {
            for (ServiceRegistration reg : registrations) {
                reg.unregister();
            }
            registrations = null;
        }
    }

    /**
     * Called in {@link #doOpen()}.
     *
     * @param pid The configuration PID to manage (ManagedService).
     */
    protected void manage(String pid) {
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, pid);
        managedServiceRegistration = bundleContext.registerService(
                "org.osgi.service.cm.ManagedService", this, props);
    }

    public void updated(Dictionary<String, ?> properties) {
        this.configuration = properties;
        reconfigure();
    }

    protected Dictionary<String, ?> getConfiguration() {
        return configuration;
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param key The configuration key
     * @param def The default value.
     * @return The value of the configuration key if found, the default value else.
     */
    protected int getInt(String key, int def) {
        if (configuration != null) {
            Object val = configuration.get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            } else if (val != null) {
                return Integer.parseInt(val.toString());
            }
        }
        return def;
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param key The configuration key.
     * @param def The default value.
     * @return The value of the configuration key if found, the default value else.
     */
    protected boolean getBoolean(String key, boolean def) {
        if (configuration != null) {
            Object val = configuration.get(key);
            if (val instanceof Boolean) {
                return (Boolean) val;
            } else if (val != null) {
                return Boolean.parseBoolean(val.toString());
            }
        }
        return def;
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param key The configuration key.
     * @param def The default value.
     * @return The value of the configuration key if found, the default value else.
     */
    protected long getLong(String key, long def) {
        if (configuration != null) {
            Object val = configuration.get(key);
            if (val instanceof Number) {
                return ((Number) val).longValue();
            } else if (val != null) {
                return Long.parseLong(val.toString());
            }
        }
        return def;
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param key The configuration key.
     * @param def The default value.
     * @return The value of the configuration key if found, the default value else.
     */
    protected String getString(String key, String def) {
        if (configuration != null) {
            Object val = configuration.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return def;
    }

    protected void reconfigure() {
        executor.submit(this);
    }

    @Override
    public void run() {
        synchronized (this) {
            doStop();
            try {
                doStart();
            } catch (Throwable t) {
                logger.warn("Error (re)starting activator", t);
                doStop();
            }
        }
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param clazz The service interface to register.
     * @param <T> The service type.
     * @param service The actual service instance to register.
     */
    protected <T> void register(Class<T> clazz, T service) {
        register(clazz, service, null);
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param clazz The service interface to register.
     * @param <T> The service type.
     * @param service The actual service instance to register.
     * @param props The service properties to register.
     */
    protected <T> void register(Class<T> clazz, T service, Dictionary<String, ?> props) {
        trackRegistration(bundleContext.registerService(clazz, service, props));
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param clazz The service interfaces to register.
     * @param service The actual service instance to register.
     */
    protected void register(Class[] clazz, Object service) {
        register(clazz, service, null);
    }

    /**
     * Called in {@link #doStart()}.
     *
     * @param clazz The service interfaces to register.
     * @param service The actual service instance to register.
     * @param props The service properties to register.
     */
    protected void register(Class[] clazz, Object service, Dictionary<String, ?> props) {
        String[] names = new String[clazz.length];
        for (int i = 0; i < clazz.length; i++) {
            names[i] = clazz[i].getName();
        }
        trackRegistration(bundleContext.registerService(names, service, props));
    }

    private void trackRegistration(ServiceRegistration registration) {
        if (registrations == null) {
            registrations = new ArrayList<>();
        }
        registrations.add(registration);
    }

}
