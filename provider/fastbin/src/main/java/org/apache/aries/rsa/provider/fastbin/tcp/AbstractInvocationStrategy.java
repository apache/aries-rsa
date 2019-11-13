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
package org.apache.aries.rsa.provider.fastbin.tcp;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.aries.rsa.provider.fastbin.Activator;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.streams.InputStreamProxy;
import org.apache.aries.rsa.provider.fastbin.streams.OutputStreamProxy;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.osgi.framework.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public abstract class AbstractInvocationStrategy implements InvocationStrategy
{

    protected final static Logger LOGGER = LoggerFactory.getLogger(AbstractInvocationStrategy.class);

    @Override
    public ResponseFuture request(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args, DataByteArrayOutputStream requestStream) throws Exception {
        replaceStreamParameters(method, args);
        encodeRequest(serializationStrategy, loader, method, args, requestStream);
        return createResponse(serializationStrategy, loader, method, args);
    }

    protected void replaceStreamParameters(Method method, Object[] args) {
        Class< ? >[] types = method.getParameterTypes();
        if(args==null)
            return;
        for (int i = 0; i < args.length; i++) {
            if(isStream(types[i])) {
                args[i] = replaceStream(args[i]);
            }
        }
    }

    protected Object replaceStream(Object value) {
        if (value instanceof InputStream) {
            InputStream in = (InputStream)value;
            int streamID = Activator.getInstance().getServer().getStreamProvider().registerStream(in);
            value = new InputStreamProxy(streamID, Activator.getInstance().getServer().getConnectAddress());
        }
        else if (value instanceof OutputStream) {
            OutputStream out = (OutputStream)value;
            int streamID = Activator.getInstance().getServer().getStreamProvider().registerStream(out);
            value = new OutputStreamProxy(streamID, Activator.getInstance().getServer().getConnectAddress());
        }
        return value;
    }

    protected boolean isStream(Class<?> clazz) {
        return clazz==InputStream.class || clazz==OutputStream.class;
    }


    /**
     * encodes the request to the stream
     * @param serializationStrategy
     * @param loader
     * @param method
     * @param args
     * @param requestStream
     * @throws Exception
     */
    protected void encodeRequest(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args, DataByteArrayOutputStream requestStream) throws Exception {
        serializationStrategy.encodeRequest(loader, method.getParameterTypes(), args, requestStream);
    }

    /**
     * creates a response for the remote method call
     * @param serializationStrategy
     * @param loader
     * @param method
     * @param args
     * @return
     * @throws Exception
     */
    protected abstract ResponseFuture createResponse(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args) throws Exception;


    @Override
    public final void service(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayInputStream requestStream, DataByteArrayOutputStream responseStream, Runnable onComplete) {
        if(method==null && target instanceof ServiceException) {
            handleInvalidRequest(serializationStrategy, loader, method, target, responseStream, onComplete);
            return;
        }
        doService(serializationStrategy, loader, method, target, requestStream, responseStream, onComplete);

    }

    protected void handleInvalidRequest(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayOutputStream responseStream, Runnable onComplete) {
        //client made an invalid request
        int pos = responseStream.position();
        try {

            Object value = null;
            Throwable error = (Throwable)target;
            serializationStrategy.encodeResponse(loader, null, value, error, responseStream);

        } catch(Exception e) {

            LOGGER.warn("Initial Encoding response for method "+method+" failed. Retrying", e);
            // we failed to encode the response.. reposition and write that error.
            try {
                responseStream.position(pos);
                serializationStrategy.encodeResponse(loader, null, null, new ServiceException(e.toString()), responseStream);
            } catch (Exception unexpected) {
                LOGGER.error("Error while servicing "+method, unexpected);
            }

        } finally {
            onComplete.run();
        }
    }

    /**
     * performs the actual remote call using the provided parameters
     * @param serializationStrategy the strategy to serialize the objects with
     * @param loader the classloader to use
     * @param method the method to call
     * @param target the object to call the method on
     * @param requestStream
     * @param responseStream
     * @param onComplete to be executed after the call has finished
     */
    protected abstract void doService(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayInputStream requestStream, DataByteArrayOutputStream responseStream, Runnable onComplete);

    protected Class getResultType(Method method) {
        return method.getReturnType();
    }

    protected class AsyncServiceResponse {

        private final ClassLoader loader;
        private final Method method;
        private final DataByteArrayOutputStream responseStream;
        private final Runnable onComplete;
        private final SerializationStrategy serializationStrategy;
        private final int pos;
        // Used to protect against sending multiple responses.
        final AtomicBoolean responded = new AtomicBoolean(false);

        public AsyncServiceResponse(ClassLoader loader, Method method, DataByteArrayOutputStream responseStream, Runnable onComplete, SerializationStrategy serializationStrategy) {
            this.loader = loader;
            this.method = method;
            this.responseStream = responseStream;
            this.onComplete = onComplete;
            this.serializationStrategy = serializationStrategy;
            pos = responseStream.position();
        }

        public void send(Throwable error, Object value) {
            if( responded.compareAndSet(false, true) ) {
                Class resultType = getResultType(method);
                try {
                    serializationStrategy.encodeResponse(loader, resultType, value, error, responseStream);
                } catch (Exception e) {
                    // we failed to encode the response.. reposition and write that error.
                    try {
                        responseStream.position(pos);
                        serializationStrategy.encodeResponse(loader, resultType, value, new ServiceException(e.toString()), responseStream);
                    } catch (Exception unexpected) {
                        LOGGER.error("Error while servicing "+method, unexpected);
                    }
                } finally {
                    onComplete.run();
                }
            }
        }
    }
}



