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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.apache.aries.rsa.provider.fastbin.Activator;

public class InputStreamProxy extends InputStream implements Serializable {

    /** field <code>serialVersionUID</code> */
    private static final long serialVersionUID = 4741860068546150748L;
    private int streamID;
    private String address;

    private transient StreamProvider streamProvider;
    private transient byte[] buffer;
    private transient int position;
    private transient int expectedChunkNumber = 0;
    private transient boolean reachedEnd = false;

    public InputStreamProxy(int streamID, String address) {
        this.streamID = streamID;
        this.address = address;
    }

    @Override
    public int read() throws IOException {
        try{
            return readInternal();
        }
        catch (IOException e) {
            // clean up on the server side
            closeSilent();
            throw e;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try{
            return super.read(b, off, len);
        }
        catch (IOException e) {
            // clean up on the server side
            closeSilent();
            throw e;
        }
    }

    /**
     * @see java.io.InputStream#read()
     */
    public int readInternal() throws IOException {
        if(buffer == null || position==buffer.length)
            fillBuffer();

        if(position==buffer.length) {
            //still no data.
            if(reachedEnd)
                return -1;
            //try again
            return read();
        }
        return (buffer[position++] & 0xFF);
    }

    private void fillBuffer() throws IOException {
        if(reachedEnd) {
            return;
        }
        position = 0;
        Chunk chunk = streamProvider.read(streamID);
        if(expectedChunkNumber!=chunk.getChunkNumber())
            throw new IOException("Stream corrupted. Received Chunk "+chunk.getChunkNumber()+" but expected "+expectedChunkNumber);
        expectedChunkNumber++;
        buffer = chunk.getData();
        reachedEnd = chunk.isLast();
    }

    public int readInternal(byte[] b, int off, int len) throws IOException {
        if(len==0)
            return 0;
        int available = available();
        if(available <= 0) {
            if(reachedEnd)
                return -1;
            fillBuffer();
            return read(b, off, len);
        }
        int processed = 0;
        int ready = Math.min(available, len);
        System.arraycopy(buffer, position, b, off, ready);
        processed += ready;
        position += ready;
        // delegate to the next chunk
        if (processed == len) {
            return processed;
        }
        int alsoRead = Math.max(0, read(b, off + processed, len - processed));
        return processed + alsoRead;
    }

    @Override
    public int available() throws IOException {
        if(buffer == null)
            return 0;
        return buffer.length - position;
    }

    @Override
    public void close() throws IOException {
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
    }

    protected void setStreamProvider(StreamProvider streamProvider) {
        this.streamProvider = streamProvider;
    }
}
