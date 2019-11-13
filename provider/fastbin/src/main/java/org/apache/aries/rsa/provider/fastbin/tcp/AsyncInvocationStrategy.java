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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.aries.rsa.provider.fastbin.api.AsyncCallback;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * </p>
 *
 */
@SuppressWarnings("rawtypes")
public class AsyncInvocationStrategy extends AbstractInvocationStrategy {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AsyncInvocationStrategy.class);

    private class AsyncResponseFuture implements ResponseFuture {

        private final ClassLoader loader;
        private final Method method;
        
        private final AsyncCallback callback;
        private final SerializationStrategy serializationStrategy;
        private final DispatchQueue queue;

        public AsyncResponseFuture(ClassLoader loader, Method method, AsyncCallback callback, SerializationStrategy serializationStrategy, DispatchQueue queue) {
            this.loader = loader;
            this.method = method;
            this.callback = callback;
            this.serializationStrategy = serializationStrategy;
            this.queue = queue;
        }

        public void set(final DataByteArrayInputStream source) {
            if( queue!=null ) {
                queue.execute(new Runnable() {
                    public void run() {
                        decodeIt(source);
                    }
                });
            } else {
                decodeIt(source);
            }
        }

        private void decodeIt(DataByteArrayInputStream source) {
            try {
                serializationStrategy.decodeResponse(loader, getResultType(method), source, callback);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            // TODO: we could store the timeout so we can time out the async request...
            return null;
        }

        @Override
        public void fail(Throwable throwable) {
            callback.onFailure(throwable);
        }
    }

    @Override
    protected void encodeRequest(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args, DataByteArrayOutputStream requestStream) throws Exception {
        Class<?>[] new_types = payloadTypes(method);
        Object[] new_args = new Object[args.length-1];
        System.arraycopy(args, 0, new_args, 0, new_args.length);
        serializationStrategy.encodeRequest(loader, new_types, new_args, requestStream);
    }

    @Override
    protected ResponseFuture createResponse(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args) throws Exception {
        return new AsyncResponseFuture(loader, method, (AsyncCallback) args[args.length-1], serializationStrategy, Dispatch.getCurrentQueue());
    }

    protected Class getResultType(Method method) {
        Type[] types = method.getGenericParameterTypes();
        ParameterizedType t = (ParameterizedType) types[types.length-1];
        return (Class) t.getActualTypeArguments()[0];
    }

    static private Class<?>[] payloadTypes(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Class<?>[] new_types = new Class<?>[types.length-1];
        System.arraycopy(types, 0, new_types, 0, new_types.length);
        return new_types;
    }

    protected void doService(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayInputStream requestStream, final DataByteArrayOutputStream responseStream, final Runnable onComplete) {

        final AsyncServiceResponse helper = new AsyncServiceResponse(loader, method, responseStream, onComplete, serializationStrategy);
        try {

            Object[] new_args = new Object[method.getParameterTypes().length];
            serializationStrategy.decodeRequest(loader, payloadTypes(method), requestStream, new_args);
            new_args[new_args.length-1] = new AsyncCallback<Object>() {
                public void onSuccess(Object result) {
                    helper.send(null, result);
                }
                public void onFailure(Throwable failure) {
                    helper.send(failure, null);
                }
            };
            method.invoke(target, new_args);

        } catch (Throwable t) {
            helper.send(t, null);
        }

    }


}
