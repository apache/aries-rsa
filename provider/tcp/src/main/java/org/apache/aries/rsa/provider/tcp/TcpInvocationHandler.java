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

    private final Deque<Connection> pool = new ArrayDeque<>();
    private int acquired; // counts connections currently in use (not in pool)
    private boolean closed;

    public TcpInvocationHandler(ClassLoader cl, String host, int port, String endpointId, int timeoutMillis)
            throws UnknownHostException, IOException {
        this.cl = cl;
        this.host = host;
        this.port = port;
        this.endpointId = endpointId;
        this.timeoutMillis = timeoutMillis;
    }

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

    // must be called exactly once for each call to acquireConnection,
    // regardless of the outcome - if there was an error, pass null
    private void releaseConnection(Connection conn) {
        synchronized (pool) {
            acquired--; // must be first
            if (conn != null) {
                pool.offerFirst(conn); // add to front of queue so old idle ones can expire
            }
            pool.notifyAll();
        }
    }

    private void closeConnection(Connection conn) throws IOException {
        if (conn != null) {
            conn.socket.close();
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
        Throwable error;
        Object result;

        try {
            conn = acquireConnection();

            // write invocation data
            conn.out.writeObject(method.getName());
            conn.out.writeObject(args);
            conn.out.flush();
            conn.out.reset();
            // read result data
            error = (Throwable)conn.in.readObject();
            result = readReplaceVersion(conn.in.readObject());

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
