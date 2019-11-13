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
package org.apache.aries.rsa.discovery.endpoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.aries.rsa.spi.EndpointDescriptionParser;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.xmlns.rsa.v1_0.EndpointDescriptionType;
import org.osgi.xmlns.rsa.v1_0.EndpointDescriptionsType;
import org.osgi.xmlns.rsa.v1_0.ObjectFactory;
import org.osgi.xmlns.rsa.v1_0.PropertyType;

@Component
public class EndpointDescriptionParserImpl implements EndpointDescriptionParser {
    private JAXBContext jaxbContext;

    public EndpointDescriptionParserImpl() {
        try {
            jaxbContext = JAXBContext.newInstance(EndpointDescriptionsType.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private List<EndpointDescriptionType> readEpdts(InputStream is) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Source source = new StreamSource(is);
            JAXBElement<EndpointDescriptionsType> jaxb = unmarshaller.unmarshal(source, EndpointDescriptionsType.class);
            EndpointDescriptionsType decorations = jaxb.getValue();
            return decorations.getEndpointDescription();
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
    
    public List<EndpointDescription> readEndpoints(InputStream is) {
        List<EndpointDescriptionType> epdts = readEpdts(is);
        List<EndpointDescription> epds = new ArrayList<>();
        for (EndpointDescriptionType epdt : epdts) {
            epds.add(convert(epdt));
        }
        return epds;
    }
    
    public EndpointDescription readEndpoint(InputStream is) {
        List<EndpointDescription> endpoints = readEndpoints(is);
        if (endpoints.isEmpty()) {
            return null;
        }
        return endpoints.iterator().next();
    }

    public void writeEndpoint(EndpointDescription epd, OutputStream os) {
        writeEpdt(convert(epd), os);
    }
    
    private void writeEpdt(EndpointDescriptionType endpointDescription, OutputStream os) {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            EndpointDescriptionsType endpointDescriptions = new EndpointDescriptionsType();
            endpointDescriptions.getEndpointDescription().add(endpointDescription);
            JAXBElement<EndpointDescriptionsType> el = new ObjectFactory().createEndpointDescriptions(endpointDescriptions);
            marshaller.marshal(el, os);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        } finally {
            safeClose(os);
        }
    }

    private void safeClose(OutputStream os) {
        try {
            os.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    private EndpointDescriptionType convert(EndpointDescription epd) {
        List<PropertyType> props = new PropertiesMapper().fromProps(epd.getProperties());
        EndpointDescriptionType epdt = new EndpointDescriptionType();
        epdt.getProperty().addAll(props);
        return epdt;
    }

    private EndpointDescription convert(EndpointDescriptionType epdt) {
        Map<String, Object> props = new PropertiesMapper().toProps(epdt.getProperty());
        return new EndpointDescription(props);
        
    }
    

}
