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
package org.apache.aries.rsa.discovery.tcp;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A message sent on the TCP connection.
 */
public class TcpMessage implements Serializable {

    public static class HandshakeMessage extends TcpMessage {

        private final String uuid;
        private final String address;
        private final List<String> peers;

        public HandshakeMessage(String uuid, String address, List<String> peers) {
            this.uuid = uuid;
            this.address = address;
            this.peers = peers;
        }

        public String getUuid() {
            return uuid;
        }

        public String getAddress() {
            return address;
        }

        public List<String> getPeers() {
            return peers;
        }
    }

    // there is no distinction between add and update, since it's
    // not the sender's job to keep track of what each peer knows
    public static class UpdateMessage extends TcpMessage {

        private final Map<String, Object> properties;

        public UpdateMessage(Map<String, Object> properties) {
            this.properties = properties;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }
    }

    public static class RemoveMessage extends TcpMessage {

        private final String endpointId;

        public RemoveMessage(String endpointId) {
            this.endpointId = endpointId;
        }

        public String getEndpointId() {
            return endpointId;
        }
    }
}
