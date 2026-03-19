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

import org.apache.aries.rsa.discovery.tcp.TcpMessage.*;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.apache.aries.rsa.discovery.tcp.TcpDiscovery.toURI;

/**
 * Manages all TCP connections for this provider, as well as
 * the higher-level TCP discovery protocol logic.
 * <p>
 * This includes accepting incoming connections on a server socket,
 * initiating outgoing connections to configured (or discovered) peers,
 * connection retry logic after unexpected disconnection, handling
 * incoming messages from connections and handling gossip-discovered peers.
 * <p>
 * In addition to the protocol-level functionality, it keeps track of
 * all known local endpoints and notifies all remote peers about them.
 */
public class TcpConnectionManager implements EndpointEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(TcpConnectionManager.class);

    private final InterestManager interestManager;
    private final String localAddress;
    private final String localUuid;
    private final long reconnectDelay;
    private final boolean gossip;

    private final ExecutorService executor = Executors.newCachedThreadPool(); // for connect/accept threads
    private final Set<TcpConnection> connections = ConcurrentHashMap.newKeySet(); // all connections, including before handshake
    private final Map<String, TcpConnection> connectionsByUuid = new ConcurrentHashMap<>(); // connections after handshake (known uuid)
    private final Map<String, EndpointDescription> localEndpoints = new ConcurrentHashMap<>();
    private final Set<String> peers = ConcurrentHashMap.newKeySet(); // all configured and discovered (gossip) peer addresses

    private ServerSocket serverSocket;
    private volatile boolean closing;

    public TcpConnectionManager(InterestManager interestManager, String localAddress,
            String localUuid, long reconnectDelay, boolean gossip) {
        this.interestManager = interestManager;
        this.localAddress = localAddress;
        this.localUuid = localUuid;
        this.reconnectDelay = reconnectDelay;
        this.gossip = gossip;
    }

    public void open(String bindAddress, int port, Collection<String> peers) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bindAddress, port));
        executor.submit(this::acceptLoop);
        addPeers(peers);
    }

    public void close() throws IOException {
        closing = true;
        serverSocket.close(); // acceptLoop will get SocketException
        connections.forEach(TcpConnection::close);
        executor.shutdownNow();
    }

    private void addPeers(Collection<String> peers) {
        peers.stream()
            .filter(peer -> !this.peers.contains(peer)) // only new ones
            .filter(peer -> !localAddress.equals(peer)) // exclude ourself
            .forEach(peer -> {
                LOG.info("Adding peer {}", peer);
                this.peers.add(peer);
                executor.submit(() -> connectLoop(peer));
            });
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> {
                    try {
                        onConnected(socket, null);
                    } catch (IOException ioe) {
                        LOG.error("error initializing connection on accepted socket", ioe);
                    }
                });
            } catch (IOException ioe) {
                return; // socket closed
            } catch (Throwable t) {
                LOG.error("Unexpected error in accept loop - shutting down", t);
                return;
            }
        }
    }

    private void connectLoop(String address) {
        URI uri = toURI(address);
        while (!closing) {
            try {
                Socket socket = new Socket(uri.getHost(), uri.getPort());
                onConnected(socket, address);
                return; // connection established; onConnectionClosed will restart this loop if needed
            } catch (IOException ioe) {
                LOG.debug("error connecting to {}, will retry soon", uri, ioe);
                try {
                    Thread.sleep(reconnectDelay);
                } catch (InterruptedException ie) {
                    // end loop (shutting down)
                    return;
                }
            } catch (Throwable t) {
                LOG.error("Unexpected error in connect loop - aborting connection to {}", uri, t);
                return;
            }
        }
    }

    private void onConnected(Socket socket, String address) throws IOException {
        try {
            TcpConnection conn = new TcpConnection(socket, address, this::onMessage, this::onClosed);
            connections.add(conn);
            conn.send(new HandshakeMessage(localUuid, localAddress, new ArrayList<>(peers)));
            // don't send known endpoints yet, only after receiving handshake
        } catch (IOException ioe) {
            try {
                socket.close();
            } catch (IOException ioe2) {
                ioe.addSuppressed(ioe2);
            }
            throw ioe;
        }
    }

    public void onClosed(TcpConnection conn) {
        connections.remove(conn);
        String peerUuid = conn.getPeerUuid();
        if (peerUuid != null) { // passed the handshake
            boolean removed = connectionsByUuid.remove(peerUuid, conn); // remove if this is the active connection
            if (removed && !closing) {
                interestManager.removePeer(peerUuid); // no active connections with peer
            }
        }
        // re-start the connect retry thread if necessary:
        // only if we're the outbound peer, and not in the process of shutting down,
        // and there isn't already an active (reverse?) connection with the same peer,
        // or we don't know who the peer is yet (before handshake)
        if (conn.isOutbound() && !closing && (peerUuid == null || !connectionsByUuid.containsKey(peerUuid))) {
            executor.submit(() -> connectLoop(conn.getPeerAddress()));
        }
    }

    private void onMessage(TcpConnection conn, TcpMessage message) {
        if (message instanceof HandshakeMessage) {
            HandshakeMessage h = (HandshakeMessage) message;
            // update the peer data
            conn.setPeerAddress(h.getAddress());
            conn.setPeerUuid(h.getUuid());
            // if gossip is enabled, try adding all of this peer's peers,
            // as well as the peer itself (in case it found us via its
            // own gossip, and we haven't met before)
            if (gossip) {
                addPeers(h.getPeers());
                addPeers(Collections.singleton(h.getAddress()));
            }
            // if we already have another connection with this peer (e.g. reverse direction)
            // then we keep the old one (which is already in use) and close the new one
            boolean existing = connectionsByUuid.putIfAbsent(h.getUuid(), conn) != null;
            if (existing) {
                // both sides can check if a connection between them already exists, but
                // there is a possible race condition where one peer receives the handshake
                // and closes the connection before it even had a chance to send its handshake -
                // so the other peer will never know the connection is intentionally closed
                // and not experiencing connection errors. If it is the outbound side, it will
                // keep retrying to connect. to solve this, only the outbound peer is the one
                // that closes the connection. The inbound side just doesn't use it until then.
                if (conn.isOutbound()) {
                    conn.close();
                }
                return;
            }
            // send all of our known local endpoints to the new peer
            localEndpoints.values().forEach(endpoint -> conn.send(new UpdateMessage(endpoint.getProperties())));
        } else if (message instanceof UpdateMessage) {
            UpdateMessage u = (UpdateMessage) message;
            EndpointDescription endpoint = new EndpointDescription(u.getProperties());
            interestManager.addEndpoint(endpoint);
        } else if (message instanceof RemoveMessage) {
            RemoveMessage r = (RemoveMessage) message;
            interestManager.removeEndpoint(conn.getPeerUuid(), r.getEndpointId());
        } else {
            throw new IllegalArgumentException("unsupported message type: " + message);
        }
    }

    @Override
    public void endpointChanged(EndpointEvent event, String filter) {
        // notify all peers of the endpoint event
        TcpMessage message;
        EndpointDescription endpoint = event.getEndpoint();
        String endpointId = endpoint.getId();
        switch (event.getType()) {
            case EndpointEvent.ADDED:
            case EndpointEvent.MODIFIED:
                localEndpoints.put(endpointId, endpoint);
                message = new UpdateMessage(endpoint.getProperties());
                break;
            case EndpointEvent.MODIFIED_ENDMATCH:
            case EndpointEvent.REMOVED:
                localEndpoints.remove(endpointId);
                message = new RemoveMessage(endpointId);
                break;
            default: throw new RuntimeException("Unknown event type: " + event.getType());
        }
        connectionsByUuid.values().forEach(c -> c.send(message));
    }
}
