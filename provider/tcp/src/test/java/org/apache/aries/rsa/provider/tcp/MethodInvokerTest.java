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
package org.apache.aries.rsa.provider.tcp;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.*;

public class MethodInvokerTest {

    @Test
    public void testExceptionThrown() {
        class Tester {
            public String throwSomething() { throw new UnsupportedOperationException(); }
        }

        Tester tester = new Tester();
        MethodInvoker invoker = new MethodInvoker(tester);
        assertThrows(InvocationTargetException.class, () -> invoker.invoke("throwSomething", null));
        assertThrows(UnsupportedOperationException.class, () -> {
            try {
                invoker.invoke("throwSomething", null);
            } catch (InvocationTargetException ite) {
                throw ite.getCause();
            }
        });
    }

    @Test
    public void testExceptionReturned() throws Exception {
        class Tester {
            public Object returnSomething() { return new UnsupportedOperationException(); }
        }

        Tester tester = new Tester();
        MethodInvoker invoker = new MethodInvoker(tester);
        assertEquals(UnsupportedOperationException.class, invoker.invoke("returnSomething", null).getClass());
    }

}