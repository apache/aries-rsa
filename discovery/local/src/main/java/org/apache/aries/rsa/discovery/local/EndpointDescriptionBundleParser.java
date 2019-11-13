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
import java.util.Collections;
import java.util.Dictionary;
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
        Enumeration<URL> urls = getEndpointDescriptionURLs(b);
        List<EndpointDescription> elements = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL resourceURL = urls.nextElement();
            try {
                elements.addAll(parser.readEndpoints(resourceURL.openStream()));
            } catch (Exception ex) {
                LOG.warn("Problem parsing: " + resourceURL, ex);
            }
        }
        return elements;
    }
    
    private Enumeration<URL> getEndpointDescriptionURLs(Bundle b) {
        String origDir = getRemoteServicesDir(b);
        
        // Split origDir into dir and file pattern
        String filePattern = "*.xml";
        String dir;
        if (origDir.endsWith("/")) {
            dir = origDir.substring(0, origDir.length() - 1);
        } else {
            int idx = origDir.lastIndexOf('/');
            if (idx >= 0 & origDir.length() > idx) {
                filePattern = origDir.substring(idx + 1);
                dir = origDir.substring(0, idx);
            } else {
                filePattern = origDir;
                dir = "";
            }
        }

        Enumeration<URL> urls = b.findEntries(dir, filePattern, false);
        return (urls == null) ? Collections.enumeration(new ArrayList<URL>()) : urls;
    }

    private static String getRemoteServicesDir(Bundle b) {
        Dictionary<?, ?> headers = b.getHeaders();
        Object header = null;
        if (headers != null) {
            header = headers.get(REMOTE_SERVICES_HEADER_NAME);
        }
        return (header == null) ? REMOTE_SERVICES_DIRECTORY : header.toString();
    }

}
