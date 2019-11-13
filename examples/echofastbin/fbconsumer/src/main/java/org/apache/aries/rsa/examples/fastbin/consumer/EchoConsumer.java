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
package org.apache.aries.rsa.examples.fastbin.consumer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.aries.rsa.examples.fastbin.api.EchoService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(immediate=true)
public class EchoConsumer {
    
    EchoService echoService;

    @Activate
    public void activate() throws IOException {
        System.out.println("Sending to echo service: echo");
        System.out.println(echoService.echo("Good morning"));
        

        System.out.println("Sending to echo service: async");
        echoService.echoAsync("Async Good morning").thenRun(() -> System.out.println("Good morning Async"));
        
        System.out.println("Sending to echo service: stream");
        InputStream inputStream = echoService.echoStream("Good morning received as a stream");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        	System.out.println(r.readLine());
        }
        
        System.out.println("Sending to echo service: stream2");
        System.out.println(echoService.echoStream2(new ByteArrayInputStream("Good morning send as a stream".getBytes(StandardCharsets.UTF_8))));   
     
    }
    

    @Reference
    public void setEchoService(EchoService echoService) throws IOException {
        this.echoService = echoService;
    }
    
}
