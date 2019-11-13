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
package org.apache.aries.rsa.provider.fastbin.tcp;

import java.net.ProtocolException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.aries.rsa.provider.fastbin.io.ProtocolCodec.BufferState;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.fusesource.hawtbuf.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

public class LengthPrefixedCodecTest {
	private ReadableByteChannel readableByteChannel = createMock(ReadableByteChannel.class);

	private WritableByteChannel writableByteChannel = createMock(WritableByteChannel.class);
	private LengthPrefixedCodec codec;

	@Before
	public void createLengthPrefixedCodec() throws Exception {
		codec = new LengthPrefixedCodec();
		codec.setReadableByteChannel(readableByteChannel);
		codec.setWritableByteChannel(writableByteChannel);
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFull() throws Exception {
		assertEquals(false, codec.full());
	}

	@Test
	public void testEmpty() throws Exception {
		assertEquals(true, codec.empty());
	}

	@Test
	public void testGetWriteCounter() throws Exception {
		assertEquals(0L, codec.getWriteCounter());
	}

	@Test
	public void testGetReadCounter() throws Exception {
		assertEquals(0L, codec.getReadCounter());
	}

	@Test
	public void testWrite() throws Exception {
		final Buffer value = Buffer.ascii("TESTDATA");

		final BufferState state = codec.write(value);

		assertEquals(BufferState.WAS_EMPTY, state);
		assertEquals(false, codec.full());
		assertEquals(false, codec.empty());
		assertEquals(0L, codec.getWriteCounter());
	}

	@Test
	public void testWrite$Twice() throws Exception {
		final Buffer value1 = Buffer.ascii("TESTDATA");
		final Buffer value2 = Buffer.ascii("TESTDATA");
		codec.write(value1);

		final BufferState state = codec.write(value2);

		assertEquals(BufferState.NOT_EMPTY, state);
		assertEquals(false, codec.full());
		assertEquals(false, codec.empty());
		assertEquals(0L, codec.getWriteCounter());
	}

	@Test
	public void testFlush() throws Exception {
		final Buffer value = Buffer.ascii("TESTDATA");
		codec.write(value);
		final int bytesThatWillBeWritten = value.length();
		expect(writableByteChannel.write((ByteBuffer) anyObject())).andAnswer(createWriteAnswer(bytesThatWillBeWritten));
		replay(writableByteChannel);

		final BufferState state = codec.flush();

		assertEquals(BufferState.EMPTY, state);
		assertEquals(false, codec.full());
		assertEquals(true, codec.empty());
		assertEquals(bytesThatWillBeWritten, codec.getWriteCounter());

		assertEquals(BufferState.WAS_EMPTY, codec.flush());
	}

	@Test
	public void testFlush$Partially() throws Exception {
		final Buffer value = Buffer.ascii("TESTDATA");
		codec.write(value);
		final int bytesThatWillBeWritten = value.length() / 2;
		expect(writableByteChannel.write((ByteBuffer) anyObject())).andAnswer(createWriteAnswer(bytesThatWillBeWritten));
		replay(writableByteChannel);

		final BufferState state = codec.flush();

		assertEquals(BufferState.NOT_EMPTY, state);
		assertEquals(false, codec.full());
		assertEquals(false, codec.empty());
		assertEquals(bytesThatWillBeWritten, codec.getWriteCounter());
	}

    @Test(expected=ProtocolException.class)
    public void testReadEvilPackage() throws Exception {

        expect(readableByteChannel.read(EasyMock.anyObject())).andAnswer(new IAnswer<Integer>() {

            @Override
            public Integer answer() throws Throwable {
                ByteBuffer buffer = (ByteBuffer)EasyMock.getCurrentArguments()[0];
                // an attacker could do that to provoke out of memory
                buffer.putInt(Integer.MAX_VALUE-1);
                return 1;
            }
        });
        replay(readableByteChannel);
        codec.read();
    }

	private IAnswer<Integer> createWriteAnswer(final int length) {
		return new IAnswer<Integer>() {
			@Override
			public Integer answer() throws Throwable {
				final ByteBuffer buffer = (ByteBuffer) getCurrentArguments()[0];
				if(buffer.remaining() < length)
					throw new BufferUnderflowException();
				buffer.position(buffer.position() + length);
				return length;
			}
		};
	}
}
