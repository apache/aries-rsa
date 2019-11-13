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
package org.apache.aries.rsa.provider.tcp.ser;

import java.io.Serializable;
import java.util.Map;

import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;

public class DTOMarker implements Serializable {
    private static final long serialVersionUID = 2248068618419940217L;

    private String className;
    private Map<String, Object> content;
    
    @SuppressWarnings("unchecked")
    public DTOMarker(Object dto) {
        Converter converter = Converters.standardConverter();
        className = dto.getClass().getName();
        content = converter.convert(dto).sourceAsDTO().to(Map.class);
    }
    
    public Object getDTO(ClassLoader classLoader) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            Converter converter = Converters.standardConverter();
            return converter.convert(content).targetAsDTO().to(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Exception deserializing DTO " + className, e);
        } 
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DTOMarker)) {
            return false;
        }
        DTOMarker other = (DTOMarker) obj;
        return className.equals(other.className) && content.equals(other.content);
    }
}
