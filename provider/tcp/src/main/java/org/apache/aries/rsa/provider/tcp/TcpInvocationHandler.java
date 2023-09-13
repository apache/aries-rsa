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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
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

public class TcpInvocationHandler implements InvocationHandler {
    private String host;
    private int port;
    private ClassLoader cl;
    private int timeoutMillis;

    public TcpInvocationHandler(ClassLoader cl, String host, int port, int timeoutMillis)
        throws UnknownHostException, IOException {
        this.cl = cl;
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
        Throwable error;
        Object result;
        try (
                Socket socket = openSocket();
                ObjectOutputStream out = new BasicObjectOutputStream(socket.getOutputStream())
            ) {
            socket.setSoTimeout(timeoutMillis);
            out.writeObject(method.getName());
            out.writeObject(args);
            out.flush();

            try (ObjectInputStream in = new BasicObjectInputStream(socket.getInputStream(), cl)) {
                error = (Throwable) in.readObject();
                result = readReplaceVersion(in.readObject());
            }
            if (error == null)
                return result;
            else if (error instanceof InvocationTargetException)
                error = error.getCause(); // exception thrown from remotely invoked method (not our problem)
            else
                throw error; // exception thrown by provider itself
        } catch (SocketTimeoutException e) {
            throw new ServiceException("Timeout calling " + host + ":" + port + " method: " + method.getName(), ServiceException.REMOTE, e);
        } catch (Throwable e) {
            throw new ServiceException("Error calling " + host + ":" + port + " method: " + method.getName(), ServiceException.REMOTE, e);
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

}
