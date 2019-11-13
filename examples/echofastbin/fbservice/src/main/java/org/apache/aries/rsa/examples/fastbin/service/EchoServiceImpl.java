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
package org.apache.aries.rsa.examples.fastbin.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.aries.rsa.examples.fastbin.api.EchoService;
import org.osgi.service.component.annotations.Component;

@Component(//
    property = {
                "service.exported.interfaces=*",
                "service.exported.configs=aries.fastbin"
    })
public class EchoServiceImpl implements EchoService {

    @Override
    public String echo(String msg) {
        return msg;
    }

	@Override
	public CompletableFuture<String> echoAsync(final String msg) {
	 	return CompletableFuture.supplyAsync(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			return msg;
		});
	}

	@Override
	public InputStream echoStream(String msg) {
		return new ByteArrayInputStream(msg.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String echoStream2(InputStream msg) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while ((length = msg.read(buffer)) != -1) {
		    result.write(buffer, 0, length);
		}
		return result.toString("UTF-8");
	}

}
