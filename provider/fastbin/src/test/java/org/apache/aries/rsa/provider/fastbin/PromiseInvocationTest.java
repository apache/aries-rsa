/**
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
package org.apache.aries.rsa.provider.fastbin;


import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.aries.rsa.provider.fastbin.InvocationTest.HelloImpl;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.tcp.ClientInvokerImpl;
import org.apache.aries.rsa.provider.fastbin.tcp.ServerInvokerImpl;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;


public class PromiseInvocationTest
{

    private ServerInvokerImpl server;
    private ClientInvokerImpl client;
    private TestService testService;


    @Before
    public void setup() throws Exception
    {
        DispatchQueue queue = Dispatch.createQueue();
        HashMap<String, SerializationStrategy> map = new HashMap<>();
        server = new ServerInvokerImpl("tcp://localhost:0", queue, map);
        server.start();

        client = new ClientInvokerImpl(queue, map);
        client.start();
//        server.stop();
        server.registerService("service-id", new ServerInvoker.ServiceFactory()
        {
            public Object get()
            {
                return new TestServiceImpl();
            }


            public void unget()
            {}
        }, TestServiceImpl.class.getClassLoader());

        InvocationHandler handler = client.getProxy(server.getConnectAddress(), "service-id", TestServiceImpl.class.getClassLoader());
        testService = (TestService)Proxy.newProxyInstance(HelloImpl.class.getClassLoader(), new Class[]{TestService.class}, handler);
    }


    @After
    public void tearDown()
    {
        server.stop();
        client.stop();
    }



    @Test
    public void testInvoke() throws Exception {
        assertEquals("Hello", testService.helloPromise().getValue());
    }

    @Test
    public void testInvokeManyThreads() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Callable<String> task = () -> testService.helloPromise().getValue();
        List<Callable<String>> tasks = new ArrayList<>();
        tasks.addAll(Collections.nCopies(threadCount, task));
        List<Future<String>> results = new ArrayList<>();
        for (Callable<String> single : tasks) {
            results.add(executor.submit(single));
        }
        assertEquals(threadCount, results.size());
        for (Future<String> future : results)
        {
            assertEquals("Hello", future.get());
        }
    }

    @Test
    public void testInvokeFutureExceptionally() throws Exception {

        Promise<String> promise = testService.exceptionPromise();
        try{
            promise.getValue();
            fail("Must throw an exception");
        } catch (InvocationTargetException e) {
            assertTrue(e.getTargetException() instanceof IOException);
            assertEquals("test", e.getCause().getMessage());
            assertTrue(promise.getFailure() instanceof IOException);
        }
    }


    public interface TestService
    {
        Promise<String> helloPromise();

        Promise<String> exceptionPromise() throws IOException;
    }

    public class TestServiceImpl implements TestService {

        @Override
        public Promise<String> helloPromise() {
            final Deferred<String> deferred = new Deferred<>();
            new Thread(() -> deferred.resolve("Hello")).start();
            return deferred.getPromise();
        }

        @Override
        public Promise<String> exceptionPromise() throws IOException {
            final Deferred<String> deferred = new Deferred<>();
            new Thread(() -> {
                sleep(500);
                deferred.fail(new IOException("test"));
            }).start();
             return deferred.getPromise();
        }

        private void sleep(long time) {
            try {
                Thread.sleep(time);
            }
            catch (InterruptedException e) {
                //NOOP
            }
        }
    }
}
