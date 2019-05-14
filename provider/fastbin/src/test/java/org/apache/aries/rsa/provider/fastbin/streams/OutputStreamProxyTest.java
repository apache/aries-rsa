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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class OutputStreamProxyTest {

    private StreamProvider streamProvider;

    @Before
    public void setUp() throws Exception {
        streamProvider = new StreamProviderImpl();
    }

    @Test
    public void testWriteFully() throws IOException {
        int charSize = 10;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int id = streamProvider.registerStream(out);
        OutputStreamProxy fixture = new OutputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        for (int i = 0; i < charSize; i++) {
            fixture.write('x');
        }
        assertEquals(0, out.size());
        fixture.close();
        assertEquals(10, out.size());
        assertEquals("xxxxxxxxxx", new String(out.toByteArray()));
    }

    @Test
    public void testWriteMixed() throws IOException {
        int charSize = StreamProviderImpl.CHUNK_SIZE*2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int id = streamProvider.registerStream(out);
        OutputStreamProxy fixture = new OutputStreamProxy(id, "");
        fixture.setStreamProvider(streamProvider);
        for (int i = 0; i < 10; i++) {
            fixture.write('x');
        }

        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        for (int i = 0; i < charSize; i++) {
            temp.write('x');
        }
        fixture.write(temp.toByteArray());
        fixture.close();
        assertEquals(10+charSize, out.size());
        byte[] byteArray = out.toByteArray();
        for (int i = 0; i < byteArray.length; i++) {
            assertEquals('x', byteArray[i]);
        }
    }

}



