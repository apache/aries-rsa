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
package org.apache.aries.rsa.provider.fastbin.streams;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

public class InputStreamProxyTest {

    private StreamProvider streamProvider;

    @Before
    public void setUp() throws Exception {
        streamProvider = new StreamProviderImpl();
    }



    @Test
    public void testUnsignedBytes() throws IOException {
        int length = 1024;
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        for(int i=0;i<length;i++)
        {
            out.write((byte)i);
        }
        byte[] data = out.toByteArray();
        byte[] result = new byte[data.length];
        int id = streamProvider.registerStream(new ByteArrayInputStream(data));

        @SuppressWarnings("resource")
        InputStreamProxy fixture = new InputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        assertEquals(length, fixture.read(result));
        assertArrayEquals(data, result);
        assertEquals(-1, fixture.read());
    }

    @Test
    public void testReadFully() throws IOException {
        int charSize = 10;
        OwnInputStream in = fillStream('c', charSize);
        int id = streamProvider.registerStream(in);
        @SuppressWarnings("resource")
        InputStreamProxy fixture = new InputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        for (int i = 0; i < charSize; i++) {
            assertEquals('c', fixture.read());
        }
        assertEquals(-1, fixture.read());
    }

    @Test
    public void testReadFullyExceedsChunkSize() throws IOException {
        int charSize = StreamProviderImpl.CHUNK_SIZE+10;
        OwnInputStream in = fillStream('c', charSize);
        int id = streamProvider.registerStream(in);
        @SuppressWarnings("resource")
        InputStreamProxy fixture = new InputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        for (int i = 0; i < charSize; i++) {
            assertEquals('c', fixture.read());
        }
        assertEquals(-1, fixture.read());
    }

    @Test
    public void testReadFullyExceedsChunkSize2() throws IOException {
        int charSize = StreamProviderImpl.CHUNK_SIZE*2;
        OwnInputStream in = fillStream('c', charSize);
        int id = streamProvider.registerStream(in);
        @SuppressWarnings("resource")
        InputStreamProxy fixture = new InputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        for (int i = 0; i < charSize; i++) {
            assertEquals('c', fixture.read());
        }
        assertEquals(-1, fixture.read());
    }

    @Test
    public void testReadArray() throws IOException {
        OwnInputStream in = fillStream('c', 1000000);
        int id = streamProvider.registerStream(in);
        @SuppressWarnings("resource")
        InputStreamProxy fixture = new InputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        assertEquals('c', fixture.read());
        assertEquals(StreamProviderImpl.CHUNK_SIZE-1, fixture.available());
        assertEquals('c', fixture.read());
        assertEquals('c', fixture.read());
        assertEquals('c', fixture.read());
        assertEquals('c', fixture.read());
        byte[] target = new byte[5];
        fixture.read(target);
        assertEquals("ccccc", new String(target));

        target = new byte[1000000-10];
        assertEquals(target.length, fixture.read(target));
        assertEquals(1000000-10, new String(target).length());
        assertEquals(-1, fixture.read(target));
    }

    @Test
    public void testClose() throws IOException {
        OwnInputStream in = fillStream('c', 10);
        int id = streamProvider.registerStream(in);
        InputStreamProxy fixture = new InputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        assertEquals('c', fixture.read());
        fixture.close();
        assertTrue(in.isClosed);
        try{
            streamProvider.read(id);
            fail("must have been closed already");
        } catch(IOException e) {};
    }

    private OwnInputStream fillStream(char c, int repetitions) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(repetitions);
        for (int i = 0; i < repetitions; i++) {
            out.write(c);
        }
        return new OwnInputStream(new ByteArrayInputStream(out.toByteArray()));
    }

    private static class OwnInputStream extends FilterInputStream {

        boolean isClosed;

        protected OwnInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            super.close();
            isClosed = true;
        }
    }
}



