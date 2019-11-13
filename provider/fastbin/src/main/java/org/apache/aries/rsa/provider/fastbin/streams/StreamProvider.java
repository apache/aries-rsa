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
import java.io.OutputStream;

import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;

/**
 * StreamProvider is a well-known service that gets auto registered in the {@link ServerInvoker}
 * to enable Input/OutputStreams in remote calls
 */
public interface StreamProvider {

    public static final String STREAM_PROVIDER_SERVICE_NAME = "stream-provider";

    /**
     * closes the specified stream and makes it inaccessible from remote
     * @param streamID
     * @throws IOException
     */
    void close(int streamID) throws IOException;

    /**
     * reads the next chunk from the specified stream
     * @param streamID
     * @return the next chunk of data
     * @throws IOException
     */
    Chunk read(int streamID) throws IOException;

    /**
     * writes the next chunk of data to the specified output stream
     * @param streamID
     * @param chunk
     * @throws IOException
     */
    void write(int streamID, Chunk chunk) throws IOException;

    /**
     * registers a new (local) input stream that will be made available for remote calls.
     * @param in
     * @return the stream id
     */
    int registerStream(InputStream in);

    /**
     * registers a new (local) output stream that will be made available for remote calls.
     * @param out
     * @return the stream id
     */
    int registerStream(OutputStream out);

}



