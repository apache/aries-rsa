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
package org.apache.aries.rsa.topologymanager.exporter;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

class RecordingEndpointEventListener implements EndpointEventListener {
    List<EndpointEvent> events = new ArrayList<>();

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        events.add(event);
    }
    
    public void matches(EndpointEvent ... endpointEvents) {
        assertThat("Incorrect number of events received", events.size(), equalTo(endpointEvents.length));
        Iterator<EndpointEvent> it = events.iterator();
        for (EndpointEvent event : endpointEvents) {
            assertThat(it.next(), samePropertyValuesAs(event));
        }
    }
     
}
