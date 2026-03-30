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
package org.apache.aries.rsa.discovery.local;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.aries.rsa.discovery.endpoint.EndpointDescriptionParserImpl;
import org.osgi.framework.Bundle;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EndpointDescriptionBundleParser {
    private static final Logger LOG = LoggerFactory.getLogger(EndpointDescriptionBundleParser.class);

    private static final String REMOTE_SERVICES_HEADER_NAME = "Remote-Service";
    private static final String REMOTE_SERVICES_DIRECTORY = "OSGI-INF/remote-service/";

    private EndpointDescriptionParserImpl parser;

    public EndpointDescriptionBundleParser() {
        parser = new EndpointDescriptionParserImpl();
    }

    public List<EndpointDescription> getAllEndpointDescriptions(Bundle b) {
        Collection<URL> urls = getEndpointDescriptionURLs(b);
        List<EndpointDescription> endpoints = new ArrayList<>();
        for (URL url : urls) {
            try {
                endpoints.addAll(parser.readEndpoints(url.openStream()));
            } catch (Exception ex) {
                LOG.warn("Problem parsing: {}", url, ex);
            }
        }
        return endpoints;
    }

    private Collection<URL> getEndpointDescriptionURLs(Bundle b) {
        String path = getRemoteServicesDir(b);

        // Split path into dir and file pattern
        String dir;
        String pattern;
        int i = path.lastIndexOf('/');
        if (i == path.length() - 1) {
            dir = path.substring(0, path.length() - 1);
            pattern = "*.xml";
        } else if (i >= 0) {
            dir = path.substring(0, i);
            pattern = path.substring(i + 1);
        } else {
            dir = "";
            pattern = path;
        }

        Enumeration<URL> urls = b.findEntries(dir, pattern, false);
        return urls == null ? Collections.emptyList() : Collections.list(urls);
    }

    private static String getRemoteServicesDir(Bundle b) {
        Object header = b.getHeaders().get(REMOTE_SERVICES_HEADER_NAME);
        return (header == null) ? REMOTE_SERVICES_DIRECTORY : header.toString();
    }

}
