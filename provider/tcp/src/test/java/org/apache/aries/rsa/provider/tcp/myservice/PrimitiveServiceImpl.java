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
package org.apache.aries.rsa.provider.tcp.myservice;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.Version;

public class PrimitiveServiceImpl implements PrimitiveService {

    @Override
    public byte callByte(byte num) {
        return num;
    }

    @Override
    public short callShort(short num) {
        return num;
    }

    
    @Override
    public int callInt(int num) {
        return num;
    }

    @Override
    public long callLong(long num) {
        return num;
    }

    @Override
    public float callFloat(float num) {
        return num;
    }

    @Override
    public double callDouble(double num) {
        return num;
    }
    
    @Override
    public boolean callBoolean(boolean bool) {
        return bool;
    }

    @Override
    public byte[] callByteAr(byte[] byteAr) {
        return byteAr;
    }

    @Override
    public Version callVersion(Version version) {
        return version;
    }
    
    @Override
    public Version[] callVersionAr(Version[] version) {
        return version;
    }

    @Override
    public List<Version> callVersionList(List<Version> version) {
        return version;
    }

    @Override
    public Map<Version, Version> callVersionMap(Map<Version, Version> map) {
        return map;
    }
    
    @Override
    public Set<Version> callVersionSet(Set<Version> set) {
        return set;
    }
    
    
    @Override
    public DTOType callDTO(DTOType dto) {
        return dto;
    }

    @Override
    public DTOType[] callDTOAr(DTOType[] dtoAr) {
        return dtoAr;
    }
}
