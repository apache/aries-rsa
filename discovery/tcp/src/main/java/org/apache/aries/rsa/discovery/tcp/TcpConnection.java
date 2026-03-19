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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A TCP connection between two TCP discovery peers.
 * <p>
 * This class handles the low-level network reading/writing/serialization,
 * while leaving the higher-level logic (including received message handling
 * and connection close handling) to the {@link TcpConnectionManager).
 */
public class TcpConnection {
    private static final Logger LOG = LoggerFactory.getLogger(TcpConnection.class);

    private final Socket socket;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;
    private final Thread readThread;
    private final boolean isOutbound;

    private final BiConsumer<TcpConnection, TcpMessage> onMessage;
    private final Consumer<TcpConnection> onClose;

    private volatile String peerAddress; // the peer host:port, for incoming connections it is set only after handshake
    private volatile String peerUuid; // set after handshake

    public TcpConnection(Socket socket, String peerAddress,
             BiConsumer<TcpConnection, TcpMessage> onMessage, Consumer<TcpConnection> onClose) throws IOException {
        this.socket = socket;
        this.isOutbound = peerAddress != null; // inbound only gets peerAddress after handshake
        this.peerAddress = peerAddress;
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.out = new ObjectOutputStream(socket.getOutputStream()); // output must be initialized before input
        this.out.flush(); // so we don't deadlock on reading object stream header
        this.in = new ObjectInputStream(socket.getInputStream());
        readThread = new Thread(null, this::readLoop, getClass().getSimpleName() + "-Reader-" + this);
        readThread.start();
    }

    public boolean isOutbound() {
        return isOutbound;
    }

    public String getPeerAddress() {
        return peerAddress;
    }

    public void setPeerAddress(String address) {
        this.peerAddress = address;
    }

    public String getPeerUuid() {
        return peerUuid;
    }

    public void setPeerUuid(String peerUuid) {
        this.peerUuid = peerUuid;
    }

    public void send(TcpMessage message) {
        try {
            synchronized (out) {
                out.writeObject(message);
                out.flush();
            }
        } catch (IOException ioe) {
            LOG.error("Error sending TCP message", ioe);
            close();
        }
    }

    public void close() {
        try {
            socket.close(); // read thread will get SocketException, fire onClose and die
        } catch (IOException ioe) {
            LOG.error("Error closing socket", ioe);
        }
    }

    private void readLoop() {
        while (true) {
            try {
                TcpMessage message = (TcpMessage)in.readObject();
                onMessage.accept(this, message);
            } catch (Throwable t) {
                if (!(t instanceof IOException)) {
                    LOG.error("Unexpected error in read loop", t);
                }
                onClose.accept(this); // invoked only here, when the socket dies for any reason
                return; // thread's dead
            }
        }
    }
}
