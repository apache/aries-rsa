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
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.provider.fastbin.api.AsyncCallback;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

@SuppressWarnings("rawtypes")
public class AsyncPromiseInvocationStrategy extends AbstractInvocationStrategy {

    @SuppressWarnings("unchecked")
    protected void doService(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayInputStream requestStream, final DataByteArrayOutputStream responseStream, final Runnable onComplete) {

        final AsyncServiceResponse helper = new AsyncServiceResponse(loader, method, responseStream, onComplete, serializationStrategy);
        try {
            Class<?>[] types = method.getParameterTypes();
            final Object[] args = new Object[types.length];
            serializationStrategy.decodeRequest(loader, types, requestStream, args);
            final Promise<Object> promise = (Promise<Object>)method.invoke(target, args);
            promise.onResolve(() -> {
                try{
                    helper.send(promise.getFailure(), promise.getFailure()==null ? promise.getValue() : null);
                }
                catch (Exception e){
                    helper.send(e, null);
                }
            });

        } catch (Throwable t) {
            helper.send(t, null);
        }
    }


    @Override
    protected ResponseFuture createResponse(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args) throws Exception {
        return new AsyncResponseFuture(loader, method, serializationStrategy, Dispatch.getCurrentQueue());
    }

    protected Class getResultType(Method method) {
        try {
            Type type = method.getGenericReturnType();
            ParameterizedType t = (ParameterizedType) type;
            return (Class) t.getActualTypeArguments()[0];
        }
        catch (Exception e) {
            return super.getResultType(method);
        }
    }

    private class AsyncResponseFuture implements ResponseFuture, AsyncCallback {

        private final ClassLoader loader;
        private final Method method;
        private final SerializationStrategy serializationStrategy;
        private final DispatchQueue queue;
        private Deferred<Object> deferred;

        public AsyncResponseFuture(ClassLoader loader, Method method, SerializationStrategy serializationStrategy, DispatchQueue queue) {
            this.loader = loader;
            this.method = method;
            this.serializationStrategy = serializationStrategy;
            this.queue = queue;
            this.deferred = new Deferred<>();
        }

        public void set(final DataByteArrayInputStream source) {
            if( queue != null ) {
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
                serializationStrategy.decodeResponse(loader, getResultType(method), source, this);
            } catch (Throwable e) {
                onFailure(e);
            }
        }

        @Override
        public void fail(Throwable throwable) {

            onFailure(throwable);
        }

        @Override
        public void onSuccess(Object result) {
            deferred.resolve(result);
        }

        @Override
        public void onFailure(Throwable failure) {
            deferred.fail(failure);
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws Exception
        {
            return deferred.getPromise();
        }
    }
}



