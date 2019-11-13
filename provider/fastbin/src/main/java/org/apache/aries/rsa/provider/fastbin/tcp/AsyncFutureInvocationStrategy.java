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
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.apache.aries.rsa.provider.fastbin.api.AsyncCallback;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

@SuppressWarnings("rawtypes")
public class AsyncFutureInvocationStrategy extends AbstractInvocationStrategy {

    private FutureCompleter completer = new FutureCompleter();

    @SuppressWarnings("unchecked")
    protected void doService(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayInputStream requestStream, final DataByteArrayOutputStream responseStream, final Runnable onComplete) {

        final AsyncServiceResponse helper = new AsyncServiceResponse(loader, method, responseStream, onComplete, serializationStrategy);
        try {
            Class<?>[] types = method.getParameterTypes();
            final Object[] args = new Object[types.length];
            serializationStrategy.decodeRequest(loader, types, requestStream, args);
            Future<Object> future = (Future<Object>)method.invoke(target, args);
            CompletableFuture<Object> completable;
            if(future instanceof CompletableFuture) {
                completable = (CompletableFuture<Object>)future;
            }
            else {
                completable = completer.complete(future);
            }
            completable.whenComplete(new BiConsumer<Object, Throwable>() {
                public void accept(Object returnValue, Throwable exception) {
                    helper.send(exception, returnValue);
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
        private CompletableFuture<Object> future;

        public AsyncResponseFuture(ClassLoader loader, Method method, SerializationStrategy serializationStrategy, DispatchQueue queue) {
            this.loader = loader;
            this.method = method;
            this.serializationStrategy = serializationStrategy;
            this.queue = queue;
            this.future = new CompletableFuture<>();
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
            future.complete(result);
        }

        @Override
        public void onFailure(Throwable failure) {
            future.completeExceptionally(failure);
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws Exception
        {
            return future;
        }
    }

    /**
     * Helper class that polls available futures in a background thread for readiness
     * and reports them to a completable future
     *
     */
    private static class FutureCompleter extends Thread {

        private ConcurrentMap<Future<Object>, CompletableFuture<Object>> futures;
        private Semaphore counter;
        private AtomicBoolean started;

        public FutureCompleter() {
            setName("Fastbin-Future-Completer");
            setDaemon(true);
            futures = new ConcurrentHashMap<>();
            counter = new Semaphore(0);
            started = new AtomicBoolean(false);
        }

        @Override
        public void run() {
            while(true) {
                // all currently available entries will be processed
                int takenPermits = Math.max(1, counter.availablePermits());
                try {
                    counter.acquire(takenPermits);
                }
                catch (InterruptedException e) {
                    continue;
                }
                Set<Entry<Future<Object>, CompletableFuture<Object >>> entrySet = futures.entrySet();
                int processed = 0;
                for (Entry<Future<Object>, CompletableFuture<Object>> entry : entrySet) {
                    if(processed == takenPermits) {
                        //we only release as many as we took permits. The remainder will be handled in the next iteration
                        break;
                    }
                    Future< ? > future = entry.getKey();
                    if(future.isDone()) {
                        try {
                            Object object = future.get();
                            entry.getValue().complete(object);
                        }
                        catch (ExecutionException e) {
                            entry.getValue().completeExceptionally(e.getCause());
                        }
                        catch (Exception e) {
                            entry.getValue().completeExceptionally(e);
                        }
                        futures.remove(future);
                        processed++;
                    }
                    else {
                        // if the future is complete, the permit is not released
                        counter.release();
                    }
                    try {
                        Thread.sleep(20);
                    }
                    catch (InterruptedException e) {
                        // sleep a little to wait for additional futures to complete
                    }
                }
            }
        }

        public CompletableFuture<Object> complete(Future<Object> future) {
            if(started.compareAndSet(false, true)) {
                start();
            }
            CompletableFuture<Object> completable = new CompletableFuture<>();
            futures.put(future, completable);
            counter.release();
            return completable;
        }
    }
}



