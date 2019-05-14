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

import java.io.Serializable;

/**
 *
 * Represents of chunk of data streamed between client and server
 * <p>
 * A chunk comes with a sequence number to verify the correct order of packages
 *
 */
public class Chunk implements Serializable {

    /** field <code>serialVersionUID</code> */
    private static final long serialVersionUID = -2809449169706358272L;
    private int chunkNumber;
    private byte[] data;
    private boolean last;

    public Chunk(byte[] data, int chunkNumber) {
        this(data, chunkNumber, false);
    }

    public Chunk(byte[] data, int chunkNumber, boolean last) {
        this.data = data;
        this.chunkNumber = chunkNumber;
        this.last = last;
    }


    public byte[] getData() {
        return data;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isLast() {
        return last;
    }
}



