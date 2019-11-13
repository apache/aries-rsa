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
package org.apache.aries.rsa.provider.tcp.myservice;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import javax.jws.Oneway;

import org.osgi.util.promise.Promise;

public interface MyService {
    String echo(String msg);

    void callSlow(int delay);
    
    void callException();
    
    // Oneway not yet supported
    @Oneway
    void callOneWay(String msg);
    
    void callWithList(List<String> msg);
    
    Future<String> callAsyncFuture(int delay);

    Promise<String> callAsyncPromise(int delay);

    CompletionStage<String> callAsyncCompletionStage(int delay); 

}
