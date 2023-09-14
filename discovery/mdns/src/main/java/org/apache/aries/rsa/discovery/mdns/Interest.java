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

import static org.osgi.service.remoteserviceadmin.EndpointEvent.ADDED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.MODIFIED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.MODIFIED_ENDMATCH;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.REMOVED;
import static org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.aries.rsa.util.StringPlus;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class Interest {
    private static final Logger LOG = LoggerFactory.getLogger(Interest.class);

    private final Long id;
    private final ConcurrentMap<String, EndpointDescription> added = new ConcurrentHashMap<>();
    private final AtomicReference<List<String>> scopes = new AtomicReference<>();
    private final Object epListener;


    public Interest(Long id, Object epListener, Map<String, Object> props) {
    	this.id = id;
        this.scopes.set(StringPlus.normalize(props.get(ENDPOINT_LISTENER_SCOPE)));
        this.epListener = epListener;
    }

    public void update(Map<String, Object> props) {
    	
    	List<String> newScopes = StringPlus.normalize(props.get(ENDPOINT_LISTENER_SCOPE));
		List<String> oldScopes = this.scopes.getAndSet(newScopes);
    	
    	added.values().removeIf(ed -> {
    		Optional<String> newScope = getFirstMatch(ed, newScopes);
    		Optional<String> oldScope = getFirstMatch(ed, oldScopes);
    		EndpointEvent event;
    		boolean remove;
    		String filter;
    		if(newScope.isPresent()) {
    			remove = false;
    			filter = newScope.get();
				if(oldScope.isPresent() && oldScope.get().equals(filter)) {
    				event = null;
    			} else {
    				event = new EndpointEvent(MODIFIED, ed);
    			}
    		} else {
    			remove = true;
    			event = new EndpointEvent(REMOVED, ed);
    			filter = oldScope.orElse(null);
    		}
    		
    		notifyListener(event, filter);
    		
    		return remove;
    	});
    }
    
    public Object getEpListener() {
        return epListener;
    }

    public void endpointChanged(EndpointDescription ed) {
    	List<String> scopes = this.scopes.get();
    	Optional<String> currentScope = getFirstMatch(ed, scopes);
    	boolean alreadyAdded = added.containsKey(ed.getId());
    	EndpointEvent event;
    	String filter;
        if (currentScope.isPresent()) {
        	if(LOG.isDebugEnabled()) {
        		LOG.debug("Listener {} is interested in endpoint {}. It will be {}", id, ed, alreadyAdded ? "MODIFIED" : "ADDED");
        	}
        	added.put(ed.getId(), ed);
			event = new EndpointEvent(alreadyAdded ? MODIFIED : ADDED, ed);
			filter = currentScope.get();
        } else if(alreadyAdded) {
        	if(LOG.isDebugEnabled()) {
        		LOG.debug("Listener {} is no longer interested in endpoint {}. It will be {}", id, ed, alreadyAdded ? "MODIFIED" : "ADDED");
        	}
        	EndpointDescription previous = added.remove(ed.getId());
        	event = new EndpointEvent(MODIFIED_ENDMATCH, ed);
        	filter = getFirstMatch(previous, scopes).orElse(null);
        } else {
        	if(LOG.isDebugEnabled()) {
        		LOG.debug("Listener {} not interested in endpoint {}", id, ed);
        	}
        	return;
        }
        
    	notifyListener(event, filter);
    }

	public void endpointRemoved(String id) {
		EndpointDescription previous = added.remove(id);
		if(previous != null) {
			if(LOG.isDebugEnabled()) {
        		LOG.debug("Endpoint {} is no longer available for listener {}", id, this.id);
        	}
        	notifyListener(new EndpointEvent(REMOVED, previous), getFirstMatch(previous, scopes.get()).orElse(null));
		}
	}

	private void notifyListener(EndpointEvent event, String filter) {
		if (epListener instanceof EndpointEventListener) {
            notifyEEListener(event, filter, (EndpointEventListener)epListener);
        } else if (epListener instanceof EndpointListener) {
            notifyEListener(event, filter, (EndpointListener)epListener);
        }
	}
    
    private Optional<String> getFirstMatch(EndpointDescription endpoint, List<String> scopes) {
        return scopes.stream().filter(endpoint::matches).findFirst();
    }

    private void notifyEEListener(EndpointEvent event, String currentScope, EndpointEventListener listener) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.info("Calling endpointchanged on class {} for filter {}, type {}, endpoint {} ",
            listener, currentScope, event.getType(), endpoint);
        listener.endpointChanged(event, currentScope);
    }

    private void notifyEListener(EndpointEvent event, String currentScope, EndpointListener listener) {
        EndpointDescription endpoint = event.getEndpoint();
        LOG.info("Calling old listener on class {} for filter {}, type {}, endpoint {} ",
            listener, currentScope, event.getType(), endpoint);
        switch (event.getType()) {
        case EndpointEvent.ADDED:
            listener.endpointAdded(endpoint, currentScope);
            break;

        case EndpointEvent.MODIFIED:
            listener.endpointRemoved(endpoint, currentScope);
            listener.endpointAdded(endpoint, currentScope);
            break;

        case EndpointEvent.REMOVED:
            listener.endpointRemoved(endpoint, currentScope);
            break;
        }
    }

    @Override
    public String toString() {
        return "Interest [scopes=" + scopes + ", epListener=" + epListener.getClass() + "]";
    }

}
