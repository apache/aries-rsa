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
package org.apache.aries.rsa.provider.fastbin.streams;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.aries.rsa.provider.fastbin.Activator;

public class OutputStreamProxy extends OutputStream implements Serializable {

    /** field <code>serialVersionUID</code> */
    private static final long serialVersionUID = -6008791618074159841L;
    private int streamID;
    private String address;
    private transient StreamProvider streamProvider;
    private transient int position;
    private transient byte[] buffer;
    private transient AtomicInteger chunkCounter;

    public OutputStreamProxy(int streamID, String address) {
        this.streamID = streamID;
        this.address = address;
        init();
    }

    private void init() {
        buffer = new byte[StreamProviderImpl.CHUNK_SIZE];
        chunkCounter = new AtomicInteger(-1);
    }

    @Override
    public void close() throws IOException {
        flush();
        streamProvider.close(streamID);
    }

    private void closeSilent() {
        try{
            close();
        } catch (Exception e) {
            //NOOP
        }
    }

    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        InvocationHandler handler = Activator.getInstance().getClient().getProxy(address, StreamProvider.STREAM_PROVIDER_SERVICE_NAME, getClass().getClassLoader());
        streamProvider = (StreamProvider)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{StreamProvider.class}, handler);
        init();
    }

    protected void setStreamProvider(StreamProvider streamProvider) {
        this.streamProvider = streamProvider;
    }

    @Override
    public void write(int b) throws IOException {
        try{
            writeInternal(b);
        } catch(IOException e) {
            closeSilent();
            throw e;
        }

    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try{
            writeInternal(b, off, len);
        } catch(IOException e) {
            closeSilent();
            throw e;
        }
    }

    public void writeInternal(int b) throws IOException {
        if(position == buffer.length)
            flush();
        buffer[position++] = (byte)b;

    }

    public void writeInternal(byte[] b, int off, int len) throws IOException {
        if(len <= 0)
            return;
        int processed = 0;
        while(processed < len) {
            int available = buffer.length - position;
            int chunkLength = Math.min(len - processed, available);
            System.arraycopy(b, off, buffer, position, chunkLength);
            position += chunkLength;
            processed += chunkLength;
            if(processed < len) {
                //there is more to go, but now the buffer is full -> flush it
                flush();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        try{
            flushInternal();
        } catch(IOException e) {
            closeSilent();
            throw e;
        }
    }

    public void flushInternal() throws IOException {
        if(position==0)
            return;
        byte[] toSend = buffer;
        if(position < buffer.length) {
            toSend = new byte[position];
            System.arraycopy(buffer, 0, toSend, 0, position);
        }
        Chunk chunk = new Chunk(toSend, chunkCounter.incrementAndGet());
        streamProvider.write(streamID, chunk);
        position = 0;
    }
}
