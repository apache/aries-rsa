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

import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

public class MyServiceImpl implements MyService {

    @Override
    public String echo(String msg) {
        return msg;
    }

    @Override
    public void callSlow(int delay) {
        sleep(delay); 
    }

    @Override
    public void callException() {
        throw new ExpectedTestException();
    }

    @Override
    public void callOneWay(String msg) {
    }

    @Override
    public void callWithList(List<String> msg) {
        
    }

    @Override
    public Future<String> callAsyncFuture(final int delay) {
        return supplyAsync(new Supplier<String>() {
            public String get() {
                if (delay == -1) {
                    throw new ExpectedTestException();
                }
                sleep(delay);
                return "Finished";
            }
            
        });
    }
    
    @Override
    public CompletionStage<String> callAsyncCompletionStage(final int delay) {
        return supplyAsync(new Supplier<String>() {
            public String get() {
                if (delay == -1) {
                    throw new ExpectedTestException();
                }
                sleep(delay);
                return "Finished";
            }
            
        });
    }
    
    @Override
    public Promise<String> callAsyncPromise(final int delay) {
        final Deferred<String> deferred = new Deferred<>();
        new Thread(new Runnable() {
            
            @Override
            public void run() {
                if (delay == -1) {
                    deferred.fail(new ExpectedTestException());
                    return;
                }
                sleep(delay);
                deferred.resolve("Finished");
            }
        }).start();
        
        return deferred.getPromise();
    }

    private void sleep(int delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
        }
    }

}
