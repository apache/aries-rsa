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

import java.lang.reflect.Method;
import java.util.concurrent.Future;

import org.apache.aries.rsa.provider.fastbin.api.AsyncCallback;
import org.osgi.util.promise.Promise;

public enum InvocationType
{
    ASYNC_FUTURE(new AsyncFutureInvocationStrategy()){

        @Override
        protected boolean applies(Method method) {
            Class<?> returnType = method.getReturnType();
            if(returnType != null) {
                return Future.class.isAssignableFrom(returnType);
            }
            return false;
        }

    }, ASYNC_CALLBACK(new AsyncInvocationStrategy()){

        @Override
        protected boolean applies(Method method) {
            Class<?>[] types = method.getParameterTypes();
            return types.length != 0 && types[types.length - 1] == AsyncCallback.class;
        }

    }, PROMISE(new AsyncPromiseInvocationStrategy()){

        @Override
        protected boolean applies(Method method) {
            if(!promiseAvailable)
                return false;
            Class<?> returnType = method.getReturnType();
            if(returnType != null) {
                return Promise.class.isAssignableFrom(returnType);
            }
            return false;
        }

    }, BLOCKING(new BlockingInvocationStrategy()){

        @Override
        protected boolean applies(Method method) {
            return true;
        }
    };

    private InvocationStrategy strategy;
    /**
     * the dependency to OSGi promise is optional. This flag
     * tracks if the class is visible or not
     */
    private static boolean promiseAvailable;

    private InvocationType(InvocationStrategy strategy) {
        this.strategy = strategy;
    }


    public static InvocationStrategy forMethod(Method method) {
        InvocationType[] values = values();
        for (InvocationType invocationType : values) {
            if(invocationType.applies(method)) {
                return invocationType.strategy;
            }
        }
        return null;
    }


    protected abstract boolean applies(Method method);

    static {
        try{
            String name = Promise.class.getName();
            // if we make it here, the class is available
            promiseAvailable = true;
        } catch (Throwable t) {
            promiseAvailable = false;
        }
    }
}



