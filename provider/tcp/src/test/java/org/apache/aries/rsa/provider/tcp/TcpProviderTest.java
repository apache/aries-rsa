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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.aries.rsa.provider.tcp.myservice.ExpectedTestException;
import org.apache.aries.rsa.provider.tcp.myservice.MyService;
import org.apache.aries.rsa.provider.tcp.myservice.MyServiceImpl;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.util.EndpointHelper;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceException;
import org.osgi.util.function.Predicate;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Success;

public class TcpProviderTest {

    private static final int TIMEOUT = 200;
    private static final int NUM_CALLS = 100;
    private static MyService myServiceProxy;
    private static Endpoint ep;
    
    @BeforeClass
    public static void createServerAndProxy() {
        Class<?>[] exportedInterfaces = new Class[] {MyService.class};
        TCPProvider provider = new TCPProvider();
        Map<String, Object> props = new HashMap<>();
        EndpointHelper.addObjectClass(props, exportedInterfaces);
        props.put("aries.rsa.hostname", "localhost");
        props.put("aries.rsa.numThreads", "10");
        props.put("osgi.basic.timeout", TIMEOUT);
        MyService myService = new MyServiceImpl();
        BundleContext bc = EasyMock.mock(BundleContext.class);
        ep = provider.exportService(myService, bc, props, exportedInterfaces);
        Assert.assertThat(ep.description().getId(), startsWith("tcp://localhost:"));
        System.out.println(ep.description());
        myServiceProxy = (MyService)provider.importEndpoint(MyService.class.getClassLoader(), 
                                                            bc,
                                                            exportedInterfaces, 
                                                            ep.description());
    }
    
    @Test
    public void testCallTimeout() {
        try {
            myServiceProxy.callSlow(TIMEOUT + 100);
            Assert.fail("Expecting timeout");
        } catch (ServiceException e) {
            assertThat(e.getCause().getClass().getName(), equalTo(SocketTimeoutException.class.getName()));
            assertThat(e.getType(), equalTo(ServiceException.REMOTE));
        }
    }

    @Test
    public void testPerf() throws InterruptedException {
        runPerfTest(myServiceProxy);
        String msg = "test";
        String result = myServiceProxy.echo(msg);
        Assert.assertEquals(msg, result);
    }
    
    @Test(expected=ExpectedTestException.class)
    public void testCallException() {
        myServiceProxy.callException();
    }
    
    @Test
    public void testCall() {
        myServiceProxy.echo("test");
    }
    
    @Test
    public void testCallOneway() {
        myServiceProxy.callOneWay("test");
    }
    
    /**
     * Test for ARIES-1515
     */
    @Test
    public void testCallWithInterfaceBasedParam() throws IOException, InterruptedException {
        List<String> msgList = new ArrayList<>();
        myServiceProxy.callWithList(msgList);
    }

    @Test
    public void testAsyncFuture() throws Exception {
        Future<String> result = myServiceProxy.callAsyncFuture(100);
        String answer = result.get(1, TimeUnit.SECONDS);
        assertEquals("Finished", answer);
    }
    
    @Test(expected = ExpectedTestException.class)
    public void testAsyncFutureException() throws Throwable {
        Future<String> result = myServiceProxy.callAsyncFuture(-1);
        try {
            result.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
    
    @Test
    public void testAsyncCompletionStage() throws Exception {
        CompletionStage<String> result = myServiceProxy.callAsyncCompletionStage(100);
        CompletableFuture<String> fresult = result.toCompletableFuture();
        String answer = fresult.get(1, TimeUnit.SECONDS);
        assertEquals("Finished", answer);
    }
    
    @Test(expected = ExpectedTestException.class)
    public void testAsyncCompletionStageException() throws Throwable {
        CompletionStage<String> result = myServiceProxy.callAsyncCompletionStage(-1);
        CompletableFuture<String> fresult = result.toCompletableFuture();
        try {
            fresult.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
    
    @Test
    public void testAsyncPromise() throws Exception {
        final Semaphore s = new Semaphore(0);
        Promise<String> p = myServiceProxy.callAsyncPromise(100);
        p.filter(new Predicate<String>() {
            @Override
            public boolean test(String x) {
                return "Finished".equals(x);
            }
        }).then(new Success<String, Object>() {
            @Override
            public Promise<Object> call(Promise<String> x)
                    throws Exception {
                s.release();
                return null;
            }
        });
        assertFalse(s.tryAcquire());
        assertTrue(s.tryAcquire(1, TimeUnit.SECONDS));
    }
    
    @Test(expected = ExpectedTestException.class)
    public void testAsyncPromiseException() throws Throwable {
        Promise<String> result = myServiceProxy.callAsyncPromise(-1);
        try {
            result.getValue();
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @AfterClass
    public static void close() throws IOException {
        ep.close();
    }

    private void runPerfTest(final MyService myServiceProxy2) throws InterruptedException {
        StringBuilder msg = new StringBuilder();
        for (int c = 0; c < 1000; c++) {
            msg.append("testing123");
        }
        final String msg2 = msg.toString();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        Runnable task = new Runnable() {
            
            @Override
            public void run() {
                String result = myServiceProxy2.echo(msg2);
                Assert.assertEquals(msg2, result);
            }
        };
        long start = System.currentTimeMillis();
        for (int c = 0; c < NUM_CALLS; c++) {
            executor.execute(task);
        }
        executor.shutdown();
        executor.awaitTermination(100, TimeUnit.SECONDS);
        long tps = NUM_CALLS * 1000 / (System.currentTimeMillis() - start);
        System.out.println(tps + " tps");
    }
    
}
