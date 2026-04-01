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
package org.apache.aries.rsa.discovery.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.karaf.shell.support.table.ShellTable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

@Component(//
    property = {
                "osgi.command.scope=rsa", //
                "osgi.command.function=endpoints",
                "osgi.command.function=endpoint",
                "endpoint.listener.scope=(endpoint.framework.uuid=*)"
    })
public class EndpointsCommand implements EndpointEventListener {
    Set<EndpointDescription> endpoints = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private String frameworkId;

    @Activate
    public void activate(BundleContext context) {
        this.frameworkId = context.getProperty(Constants.FRAMEWORK_UUID);
    }

    public void endpoint(String id) {
        EndpointDescription epd = getEndpoint(id);
        ShellTable table = new ShellTable();
        table.column("key");
        table.column("value");

        for (String key : epd.getProperties().keySet()) {
            Object value = epd.getProperties().get(key);
            table.addRow().addContent(key, toString(value));
        }
        table.print(System.out);
    }

    private Object toString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            return Arrays.toString((Object[])value);
        }
        return value.toString();
    }

    private EndpointDescription getEndpoint(String id) {
        for (EndpointDescription epd : endpoints) {
            if (epd.getId().equals(id)) {
                return epd;
            }
        }
        throw new IllegalArgumentException("No endpoint found for id " + id);
    }

    public void endpoints() {
        System.out.println("Endpoints for framework " + frameworkId);
        ShellTable table = new ShellTable();
        table.column("id");
        table.column("interfaces");
        table.column("framework");
        table.column("comp name");
        for (EndpointDescription epd : endpoints) {
            print(table, epd);
        }
        table.print(System.out);
    }

    private void print(ShellTable table, EndpointDescription ep) {
        String compName = getProp(ep, "component.name");
        table.addRow().addContent(ep.getId(), ep.getInterfaces(), ep.getFrameworkUUID(), compName);
    }

    private String getProp(EndpointDescription ep, String key) {
        Object value = ep.getProperties().get(key);
        return value == null ? "" : value.toString();
    }

    @Override
    public void endpointChanged(EndpointEvent event, String matchedFilter) {
        EndpointDescription endpoint = event.getEndpoint();
        switch (event.getType()) {
        case EndpointEvent.ADDED:
            endpoints.add(endpoint);
            break;

        case EndpointEvent.REMOVED:
        case EndpointEvent.MODIFIED_ENDMATCH:
            endpoints.remove(endpoint);
            break;

        case EndpointEvent.MODIFIED:
            endpoints.remove(endpoint);
            endpoints.add(endpoint);
            break;
        }
    }

}
