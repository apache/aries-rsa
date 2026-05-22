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
package org.apache.aries.rsa.core;

import org.apache.aries.rsa.util.StringPlus;
import org.osgi.framework.*;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * This service acts as a bridge between those consumers/producers of endpoint
 * events that support only the deprecated {@link EndpointListener} interface and
 * those that support only the newer {@link EndpointEventListener} interface.
 * <p>
 * According to the RSA specification, all actors must be backward-compatible
 * with the old interface - they must listen to both and notify both, with the
 * caveat that if a listener implements both interfaces, notifiers should only
 * send it events using the newer API in order to prevent event duplication.
 * <p>
 * Components in the Aries RSA implementation now only support the newer
 * interface natively, as inter-compatibility with components using the old
 * interface are rarely needed. However, for spec compatibility, TCK (compliance)
 * tests, and those rare occasions - this bridge fills in the gap when needed.
 * <p>
 * Consumer (listener) and producer (notifier) implementations may support
 * either only the old interface, or only the new interface, or both.
 * <p>
 * If they support both, they can directly notify peers of all the three types,
 * and, trivially, new-only or old-only components already work with equivalent
 * peers. So the only cases that need to be bridged are a pair of new-only and
 * old-only peers that need to communicate with each other. Handling any other
 * case via the bridge could produce duplicate events and must be avoided.
 * <p>
 * The bridge is implemented using four mechanisms:
 * <ol>
 *     <li>A {@link ServiceListener} keeps track of all registered services
 *         implementing {@code EndpointListener} (old consumers) and
 *         {@code EndpointEventListener} (new consumers), and ignores those
 *         that implement both.
 *     </li>
 *     <li>A {@link ListenerHook} keeps track of all registered
 *         ServiceListeners (including ServiceTrackers) that are looking
 *         for services implementing {@code EndpointListener} (old producers)
 *         or {@code EndpointEventListener} (new producers).
 *     </li>
 *     <li>A registered service that implements both interfaces (our own
 *         listener/consumer) that is backed by a {@link ServiceFactory}
 *         that gives each calling bundle (producer bundle) its own
 *         {@code Adapter} instance.
 *     </li>
 *     <li>
 *         An {@link Adapter Adapter} service object (per bundle) that
 *         implements both interfaces, and depending on the interface types
 *         supported by the producer bundle associated with it, either does
 *         nothing (if the producer supports both interfaces or neither
 *         of them) or converts invocations of the one interface supported
 *         by the producer into a notification sent to all consumers of the
 *         opposite type (old producer to all new consumers or new producer
 *         to all old consumers).
 *     </li>
 * </ol>
 * <p>
 * Note that due to the limitations of {@code ListenerHook}, the adapter
 * conversion policy is determined per bundle and not per individual producer
 * within the bundle. This works as long as the (reasonable) assumption is
 * made that all producers within the same bundle implement the same
 * interface version (or both of them).
 *
 * @deprecated this class exists only for spec-required backwards compatibility
 * with the {@code EndpointListener} interface, which is itself deprecated
 * in the spec. It will be removed when {@code EndpointListener} is removed
 * from the spec. Users of code or libraries that still use {@code EventListener}
 * exclusively are urged to upgrade to use of {@code EndpointEventListener}
 * rather than relying on this bridge moving forward.
 */
@SuppressWarnings("deprecation")
public class EventListenerBridge implements ServiceListener, ListenerHook {

    /**
     * An adapter service that can convert invocation of one of the endpoint
     * listener interfaces to the other, based on the producer interface types.
     * <p>
     * Note that it will only dispatch events if the producer supports exactly one
     * type of listener (if it supports both or none, it must not be bridged).
     */
    private class Adapter implements EndpointListener, EndpointEventListener {

        long bundleId;

        /**
         * Creates an adapter service instance for the given bundle's producer types.
         */
        Adapter(long bundleId) {
            this.bundleId = bundleId;
        }

        @Override
        public void endpointChanged(EndpointEvent event, String filter) {
            if (producers.get(bundleId) == ENDPOINT_EVENT_LISTENER) {
                dispatchToOld(event);
            }
        }

        @Override
        public void endpointAdded(EndpointDescription endpoint, String filter) {
            if (producers.get(bundleId) == ENDPOINT_LISTENER) {
                dispatchToNew(new EndpointEvent(EndpointEvent.ADDED, endpoint));
            }
        }

        @Override
        public void endpointRemoved(EndpointDescription endpoint, String filter) {
            if (producers.get(bundleId) == ENDPOINT_LISTENER) {
                dispatchToNew(new EndpointEvent(EndpointEvent.REMOVED, endpoint));
            }
        }
    }

    private class AdapterServiceFactory implements ServiceFactory<Adapter> {
        @Override
        public Adapter getService(Bundle bundle, ServiceRegistration<Adapter> reg) {
            // provide a new adapter instance per producer bundle, according to its supported types
            return new Adapter(bundle.getBundleId());
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration<Adapter> reg, Adapter service) {
        }
    };

    // bitmask of supported listener types
    private static final int
        ENDPOINT_LISTENER = 1,
        ENDPOINT_EVENT_LISTENER = 2;

    private static final String
        OBJECT_CLASS_FILTER_PREFIX = "(" + Constants.OBJECTCLASS + "=",
        OWN_LISTENER_PROP = EventListenerBridge.class.getName(),
        ENDPOINT_LISTENER_CLASS_NAME = EndpointListener.class.getName(),
        ENDPOINT_EVENT_LISTENER_CLASS_NAME = EndpointEventListener.class.getName(),
        SERVICE_LISTENER_FILTER = "(&(|" + getObjectClass(EndpointListener.class)
            + getObjectClass(EndpointEventListener.class) + ")(!(" + OWN_LISTENER_PROP + "=*)))";

    private final Set<ServiceReference<EndpointListener>> oldConsumers =
        Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ServiceReference<EndpointEventListener>> newConsumers =
        Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Long, Integer> producers = new ConcurrentHashMap<>(); // bundleId to producer types bitmap

    private final BundleContext context;
    private ServiceRegistration<ServiceFactory<Adapter>> factoryRegistration;
    private ServiceRegistration<ListenerHook> hookRegistration;

    public EventListenerBridge(BundleContext context) {
        this.context = context;
    }

    private static String getObjectClass(Class<?> cls) {
        return OBJECT_CLASS_FILTER_PREFIX + (cls == null ? "*" : cls.getName()) + ")";
    }

    private static Collection<String> getObjectClasses(String filter) {
        if (filter == null)
            return Collections.emptyList();
        Collection<String> classes = new ArrayList<>(2);
        int i = 0;
        while (true) {
            i = filter.indexOf(OBJECT_CLASS_FILTER_PREFIX, i);
            if (i < 0) {
                return classes;
            }
            i += OBJECT_CLASS_FILTER_PREFIX.length();
            int j = filter.indexOf(')', i);
            classes.add(filter.substring(i, j));
            i = j;
        }
    }

    private String match(ServiceReference<?> sref, EndpointDescription endpoint) {
        List<String> filters = StringPlus.normalize(sref.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE));
        for (String filter : filters) {
            try {
                Filter f = context.createFilter(filter);
                if (f.matches(endpoint.getProperties()))
                    return filter;
            } catch (InvalidSyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void start() throws InvalidSyntaxException {
        // ServiceListener to track consumers
        context.addServiceListener(this, SERVICE_LISTENER_FILTER);
        // ListenerHook to track what producers are looking for
        hookRegistration = context.registerService(ListenerHook.class, this, null);
        // our listener, backed by a ServiceFactory, to be used by producers
        factoryRegistration = (ServiceRegistration<ServiceFactory<Adapter>>)
            context.registerService(
                new String[] { ENDPOINT_LISTENER_CLASS_NAME, ENDPOINT_EVENT_LISTENER_CLASS_NAME },
                new AdapterServiceFactory(), getListenerProperties());
    }

    public void stop() {
        if (factoryRegistration != null) {
            factoryRegistration.unregister();
        }
        if (hookRegistration != null) {
            hookRegistration.unregister();
        }
        context.removeServiceListener(this);
    }

    private Hashtable<String, Object> getListenerProperties() {
        // our listener's scope is the combined scope of all known consumers we may dispatch to
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(OWN_LISTENER_PROP, "true"); // mark our own listener for exclusion from consumers
        String[] scopes = Stream.concat(oldConsumers.stream(), newConsumers.stream())
            .flatMap(s -> StringPlus.normalize(s.getProperty(EndpointEventListener.ENDPOINT_LISTENER_SCOPE)).stream())
            .toArray(String[]::new);
        props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, scopes);
        return props;
    }

    @Override // ServiceListener - track consumers that publish EL/EEL services
    @SuppressWarnings("unchecked")
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> sref = event.getServiceReference();
        List<String> classes = Arrays.asList((String[])sref.getProperty(Constants.OBJECTCLASS));
        boolean el = classes.contains(ENDPOINT_LISTENER_CLASS_NAME);
        boolean eel = classes.contains(ENDPOINT_EVENT_LISTENER_CLASS_NAME);
        if (event.getType() == ServiceEvent.UNREGISTERING)
            el = eel = false; // remove both
        // update our consumer lists with relevant one-interface-only consumers
        boolean modified = false;
        if (!el)
            modified |= oldConsumers.remove(sref);
        else if (!eel) // only EL (not EEL)
            modified |= oldConsumers.add((ServiceReference<EndpointListener>)sref);
        if (!eel)
            modified |= newConsumers.remove(sref);
        else if (!el) // only EEL (not EL)
            modified |= newConsumers.add((ServiceReference<EndpointEventListener>)sref);
        // update our own listener's scopes accordingly
        if (modified) {
            factoryRegistration.setProperties(getListenerProperties());
        }
    }

    /**
     * Updates the supported endpoint listener types (EE/EEL) that producers
     * are looking for, by tracking all registered service listeners
     * (via ListenerHook) and checking their filters for our interface types.
     *
     * @param listeners the updated (added/removed) service listener's info
     */
    private void updateProducerNeeds(Collection<ListenerInfo> listeners) {
        for (ListenerInfo info : listeners) {
            String filter = info.getFilter();
            if (filter != null) {
                Collection<String> classes = getObjectClasses(filter);
                int types = (classes.contains(ENDPOINT_LISTENER_CLASS_NAME) ? ENDPOINT_LISTENER : 0)
                    | (classes.contains(ENDPOINT_EVENT_LISTENER_CLASS_NAME) ? ENDPOINT_EVENT_LISTENER : 0);
                if (types != 0) {
                    long bundleId = info.getBundleContext().getBundle().getBundleId();
                    boolean remove = info.isRemoved();
                    producers.compute(bundleId, (id, t) -> {
                        t = t == null ? 0 : t;
                        if (remove)
                            t &= ~types; // remove types
                        else
                            t |= types; // add types
                        return t == 0 ? null : t; // remove from map if no types
                    });
                }
            }
        }
    }

    @Override // ListenerHook - track what listeners potential producers are looking for
    public void added(Collection<ListenerInfo> listeners) {
        // note: if a listener is registered again but with a different filter,
        // the old one is automatically removed first before the new one is added
        // also, the ListenerInfo instance passed to add and remove for the same
        // listener service will be equal (if we want to keep closer track of them)
        updateProducerNeeds(listeners);
    }

    @Override // ListenerHook - track what listeners potential producers are looking for
    public void removed(Collection<ListenerInfo> listeners) {
        updateProducerNeeds(listeners);
    }

    /**
     * Dispatches an endpoint event from a new producer to all old consumers that match it.
     *
     * @param event the event to dispatch
     */
    private void dispatchToOld(EndpointEvent event) {
        EndpointDescription endpoint = event.getEndpoint();
        oldConsumers.forEach(sref -> {
            String filter = match(sref, endpoint);
            if (filter != null) {
                EndpointListener listener = context.getService(sref);
                if (listener != null) {
                    try {
                        switch (event.getType()) {
                            case EndpointEvent.ADDED:
                                listener.endpointAdded(endpoint, filter);
                                break;
                            case EndpointEvent.REMOVED:
                                listener.endpointRemoved(endpoint, filter);
                                break;
                            case EndpointEvent.MODIFIED:
                            case EndpointEvent.MODIFIED_ENDMATCH:
                                listener.endpointRemoved(endpoint, filter);
                                listener.endpointAdded(endpoint, filter);
                                break;
                        }
                    } finally {
                        context.ungetService(sref);
                    }
                }
            }
        });
    }

    /**
     * Dispatches an endpoint event from an old producer to all new consumers that match it.
     *
     * @param event the event to dispatch
     */
    private void dispatchToNew(EndpointEvent event) {
        newConsumers.forEach(sref -> {
            String filter = match(sref, event.getEndpoint());
            if (filter != null) {
                EndpointEventListener listener = context.getService(sref);
                if (listener != null) {
                    try {
                        listener.endpointChanged(event, filter);
                    } finally {
                        context.ungetService(sref);
                    }
                }
            }
        });
    }
}
