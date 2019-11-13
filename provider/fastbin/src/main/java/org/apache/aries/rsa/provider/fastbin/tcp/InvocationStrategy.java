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

import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;

/**
 * <p>
 * </p>
 *
 */
public interface InvocationStrategy {

    public ResponseFuture request(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object[] args, DataByteArrayOutputStream requestStream) throws Exception;

    /**
     * handles the actual remote call.
     * <p>
     * if method is <code>null</code> and target is a <code>ServiceException</code> it is treated as an indication that the method lookup failed.
     * In such a case the strategy will send the service exception to the caller
     * @param serializationStrategy
     * @param loader
     * @param method
     * @param target
     * @param requestStream
     * @param responseStream
     * @param onComplete
     */
    void service(SerializationStrategy serializationStrategy, ClassLoader loader, Method method, Object target, DataByteArrayInputStream requestStream, DataByteArrayOutputStream responseStream, Runnable onComplete);
}
