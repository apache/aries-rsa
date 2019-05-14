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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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


public class StreamInvocationTest {

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
        Activator.INSTANCE = new Activator();
        Activator.INSTANCE.client = client;
        Activator.INSTANCE.server = server;
    }


    @After
    public void tearDown()
    {
        server.stop();
        client.stop();
    }


    @Test
    public void testToString() throws IOException {
        assertEquals("Test", testService.toString(new ByteArrayInputStream("Test".getBytes())));

    }

    @Test(timeout=5000)
    public void testToStringLarge() throws IOException {
        InputStream in = fillStream('a', 1000000);
        long time = System.currentTimeMillis();
        String result = testService.toString(in); //roughly 1 MB of data
        System.out.println("Transfered 1MB of data in "+(System.currentTimeMillis()-time)+"ms");
        assertEquals(1000000, result.length());
        for(int i=0;i<result.length();i++) {
            assertEquals('a', result.charAt(i));
        }

    }


    @Test
    public void testToStream() throws IOException {
        assertEquals("Test", new BufferedReader(new InputStreamReader(testService.toStream("Test"))).readLine());

    }

    @Test(timeout=5000)
    public void testToStreamLarge() throws IOException {
        String string = fillBuffer('a', 1000000);
        long time = System.currentTimeMillis();
        InputStream stream = testService.toStream(string); //roughly 1 MB of data
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String result = reader.readLine();
        System.out.println("Transfered 1MB of data in "+(System.currentTimeMillis()-time)+"ms");
        assertEquals(1000000, result.length());
        for(int i=0;i<result.length();i++) {
            assertEquals('a', result.charAt(i));
        }

    }

    @Test
    public void testIntoStream() throws IOException, InterruptedException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        testService.intoStream(result, "Test");
        Thread.sleep(100);
        assertEquals("Test", new String(result.toByteArray()));

    }

    @Test
    public void testFutureAndStream() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        String testString = "This is a test";
        MessageDigest digester = MessageDigest.getInstance("MD5");
        byte[] digest = digester.digest(testString.getBytes());
        Future<byte[]> future = testService.digest(new ByteArrayInputStream(testString.getBytes()));
        assertArrayEquals(digest, future.get());

    }

    public interface TestService {
        String toString(InputStream in) throws IOException;

        InputStream toStream(String s) throws IOException;

        void intoStream(OutputStream out, String string) throws IOException;

        Future<byte[]> digest(InputStream in) throws IOException;
    }

    public class TestServiceImpl implements TestService {
        @Override
        public String toString(InputStream in) throws IOException {
            StringBuilder b = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                b.append(r.readLine());
            }
            return b.toString();
        }

        @Override
        public InputStream toStream(String s) throws IOException {
            return new ByteArrayInputStream(s.getBytes());
        }

        @Override
        public void intoStream(final OutputStream out, String string) throws IOException {
            new Thread(() -> {
                try{
                    out.write(string.getBytes());
                    out.close();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        @Override
        public Future<byte[]> digest(InputStream in) throws IOException {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    MessageDigest digest = MessageDigest.getInstance("MD5");
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int i;
                    while((i = in.read()) != -1) {
                        out.write(i);
                    }
                    byte[] md5 = digest.digest(out.toByteArray());
                    return md5;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            });
        }
    }

    protected InputStream fillStream(char c, int repetitions) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i=0; i<repetitions; i++){
            out.write(c);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    protected String fillBuffer(char c, int repetitions) {
        StringBuilder b = new StringBuilder(repetitions);
        for (int i = 0; i < repetitions; i++) {
            b.append(c);
        }
        return b.toString();
    }
}
