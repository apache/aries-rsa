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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamProviderImpl implements StreamProvider {

    private ConcurrentHashMap<Integer, Closeable> streams = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, AtomicInteger> chunks = new ConcurrentHashMap<>();
    private AtomicInteger counter = new AtomicInteger(0);
    protected static final int CHUNK_SIZE = 4096 * 16; //64k
    private static final byte[] EMPTY = new byte[0];

    ThreadLocal<byte[]> buffer = new ThreadLocal<byte[]>(){
        @Override
        protected byte[] initialValue() {
            return new byte[CHUNK_SIZE];
        }
    };

    public int registerStream(InputStream in) {
        int streamID = counter.incrementAndGet();
        streams.put(streamID, in);
        chunks.put(streamID, new AtomicInteger(-1));
        return streamID;
    }

    @Override
    public int registerStream(OutputStream out) {
        int streamID = counter.incrementAndGet();
        streams.put(streamID, out);
        chunks.put(streamID, new AtomicInteger(-1));
        return streamID;
    }

    @Override
    public void close(int streamID) throws IOException {
        Closeable stream = streams.remove(streamID);
        chunks.remove(streamID);
        if(stream != null) {
            stream.close();
        }
    }

    @Override
    public Chunk read(int streamID) throws IOException {
        InputStream inputStream = getStream(streamID);
        AtomicInteger chunkNumber = chunks.get(streamID);
        byte[] result = buffer.get();
        int read = inputStream.read(result);
        if(read<0) {
            close(streamID); //we are finished, best clean it up right away
            return new Chunk(EMPTY, chunkNumber.incrementAndGet(), true);
        }
        if(read!=result.length) {
            byte[] tmp = new byte[read];
            System.arraycopy(result, 0, tmp, 0, read);
            result = tmp;
        }
        return new Chunk(result, chunkNumber.incrementAndGet());
    }

    @Override
    public void write(int streamID, Chunk chunk) throws IOException {
        OutputStream out = getStream(streamID);
        int nextChunkNumber = chunks.get(streamID).incrementAndGet();
        if(chunk.getChunkNumber() != nextChunkNumber) {
            throw new IOException("Stream corrupted. Received Chunk "+chunk.getChunkNumber()+" but expected "+nextChunkNumber);
        }
        out.write(chunk.getData());
    }

    @SuppressWarnings({"unchecked"})
    private <T extends Closeable> T getStream(int id) throws IOException {
        Closeable closeable = streams.get(id);
        if(closeable == null)
            throw new IOException("No Stream with id " + id + "available");
        try {
            return (T)closeable;
        }
        catch (ClassCastException e) {
            throw new IOException("No Stream with id " + id + "available");
        }
    }

}
