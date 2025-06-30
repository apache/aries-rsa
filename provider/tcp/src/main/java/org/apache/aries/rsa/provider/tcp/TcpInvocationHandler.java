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
package org.apache.aries.rsa.provider.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.aries.rsa.provider.tcp.ser.BasicObjectInputStream;
import org.apache.aries.rsa.provider.tcp.ser.BasicObjectOutputStream;
import org.apache.aries.rsa.provider.tcp.ser.VersionMarker;
import org.osgi.framework.ServiceException;
import org.osgi.framework.Version;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

/**
 * The InvocationHandler backing the client-side service proxy,
 * which sends the details of the method invocations
 * over a TCP connection, to be executed by the remote service.
 */
public class TcpInvocationHandler implements InvocationHandler, Closeable {

    private static class Connection {
        Socket socket;
        BasicObjectOutputStream out;
        BasicObjectInputStream in;

        public Connection(Socket socket) throws IOException {
            this.socket = socket;
            out = new BasicObjectOutputStream(socket.getOutputStream());
            in = new BasicObjectInputStream(socket.getInputStream());
        }
    }

    private String host;
    private int port;
    private String endpointId;
    private ClassLoader cl;
    private int timeoutMillis;

    /**
     * The available (idle) pool of connections.
     */
    private final Deque<Connection> pool = new ArrayDeque<>();

    /**
     * Counts connections currently in use (not in pool).
     */
    private int acquired;

    private boolean closed;

    TcpInvocationHandler(ClassLoader cl, String host, int port, String endpointId, int timeoutMillis)
            throws UnknownHostException, IOException {
        this.cl = cl;
        this.host = host;
        this.port = port;
        this.endpointId = endpointId;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Acquires a connection - either from the pool if there is
     * one available, or creates a new one if the pool is empty.
     * <p>
     * For each invocation of this method, the {@link #releaseConnection}
     * method must be called exactly once (even if there was an error!)
     *
     * @return a connection
     * @throws IOException if an error occurs
     */
    private Connection acquireConnection() throws IOException {
        Connection conn;
        synchronized (pool) {
            acquired++; // must be first
            if (closed) {
                throw new IOException("Connection pool is closed");
            }
            conn = pool.pollFirst(); // reuse most recently used connection
        }
        // if the pool is empty, create a new connection
        if (conn == null) {
            conn = new Connection(openSocket());
            conn.socket.setSoTimeout(timeoutMillis);
            conn.socket.setTcpNoDelay(true);
            conn.in.addClassLoader(cl);
            conn.out.writeUTF(endpointId); // select endpoint for this connection
        }
        return conn;
    }

    /**
     * Releases the given connection and returns it to the pool.
     * <p>
     * This method must be called exactly once for each invocation
     * of {@link #acquireConnection()}, regardless of the outcome -
     * if there was an error, it should be invoked with a null parameter.
     *
     * @param conn the released connection, or null if there
     *             was an error when attempting to acquire one
     */
    private void releaseConnection(Connection conn) {
        synchronized (pool) {
            acquired--; // must be first
            if (conn != null) {
                pool.offerFirst(conn); // add to front of queue so old idle ones can expire
            }
            pool.notifyAll();
        }
    }

    private void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.socket.close();
            } catch (IOException ignore) {
                // we want it closed - nothing else to do
            }
        }
    }

    private void closeConnections() throws IOException {
        synchronized (pool) {
            closed = true; // first prevent acquiring new connections
            while (true) {
                // close all idle connections
                for (Iterator<Connection> it = pool.iterator(); it.hasNext(); ) {
                    closeConnection(it.next());
                    it.remove();
                }
                if (acquired == 0) {
                    break; // all closed
                }
                // wait for additional active connections to be released
                try {
                    pool.wait();
                } catch (InterruptedException ie) {
                    throw new IOException("interrupted while closing connections", ie);
                }
            }
        }
    }

    private int getPoolSize() {
        synchronized (pool) {
            return pool.size() + acquired; // both idle and active
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // handle Object methods locally so we can use equals, HashMap, etc. normally
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return proxy.getClass().getName() + "@"
                    + Integer.toHexString(System.identityHashCode(proxy));
            }
        }
        // handle remote invocation
        if (Future.class.isAssignableFrom(method.getReturnType()) ||
            CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            return createFutureResult(method, args);
        } else if (Promise.class.isAssignableFrom(method.getReturnType())) {
            return createPromiseResult(method, args);
        } else {
            return handleSyncCall(method, args);
        }
    }

    private Object createFutureResult(final Method method, final Object[] args) {
        return CompletableFuture.supplyAsync(new Supplier<Object>() {
            public Object get() {
                try {
                    return handleSyncCall(method, args);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private Object createPromiseResult(final Method method, final Object[] args) {
        final Deferred<Object> deferred = new Deferred<>();
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    deferred.resolve(handleSyncCall(method, args));
                } catch (Throwable e) {
                    deferred.fail(e);
                }
            }
        }).start();
        return deferred.getPromise();
    }

    private Object handleSyncCall(Method method, Object[] args) throws Throwable {
        Connection conn = null;
        Throwable error = null;
        Object result = null;

        try {
             // try at most all existing connections (which may be stale) plus one new
            for (int attempts = getPoolSize() + 1; attempts > 0; attempts--) {
                conn = acquireConnection(); // get or create pool connection
                try {
                    // write invocation data
                    conn.out.writeObject(method.getName());
                    conn.out.writeObject(args);
                    conn.out.flush();
                    conn.out.reset();
                    // read result data
                    error = (Throwable) conn.in.readObject();
                    result = readReplaceVersion(conn.in.readObject());
                    break; // transaction completed
                } catch (SocketException se) { // catch only read/write exceptions here - only stale connections
                    if (attempts == 1) {
                        throw se; // failed last attempt - propagate the error
                    }
                    // the server socket was previously open, but now failed -
                    // communication error or server socket was closed (e.g. idle timeout)
                    // so we retry with another connection
                    releaseConnection(null); // dispose of it before next attempt
                }
            }

            if (error == null)
                return result;
            else if (error instanceof InvocationTargetException)
                error = error.getCause(); // exception thrown from remotely invoked method (not our problem)
            else
                throw error; // exception thrown by provider itself
        } catch (Throwable e) {
            // this can be an unexpected error from remote (not from the invoked method itself
            // but somewhere in the provider processing), or a communications error (e.g. timeout) -
            // in either case we don't know what was written or not, so we must abort the connection
            closeConnection(conn);
            conn = null; // don't return it to the pool
            throw new ServiceException("Error invoking " + method.getName() + " on " + endpointId, ServiceException.REMOTE, e);
        } finally {
            releaseConnection(conn);
        }
        throw error;
    }

    private Socket openSocket() throws UnknownHostException, IOException {
        return AccessController.doPrivileged(new PrivilegedAction<Socket>() {

            @Override
            public Socket run() {
                try {
                    return new Socket(host, port);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        });
    }

    private Object readReplaceVersion(Object readObject) {
        if (readObject instanceof VersionMarker) {
            return new Version(((VersionMarker)readObject).getVersion());
        } else {
            return readObject;
        }
    }

    @Override
    public void close() throws IOException {
        closeConnections();
    }
}
