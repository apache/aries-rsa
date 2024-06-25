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
import java.util.function.Function;

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

    @Test
    public void testNullParam() throws Exception {
        class Tester {
            public int f(String s) { return s == null ? 0 : s.length(); }
        }
        MethodInvoker invoker = new MethodInvoker(new Tester());
        assertEquals(0, invoker.invoke("f", new Object[]{ null }));
    }

    @Test
    public void testOverloadedNumberOfParams() throws Exception {
        class Tester {
            public int sum() { return 0; }
            public int sum(int i) { return i; }
            public int sum(int i, int j) { return i + j; }
        }

        Tester tester = new Tester();
        tester.sum((short)1);
        tester.sum(Short.valueOf((short)1));

        MethodInvoker invoker = new MethodInvoker(tester);
        assertEquals(0, invoker.invoke("sum", null));
        assertEquals(0, invoker.invoke("sum", new Object[] {}));
        assertEquals(1, invoker.invoke("sum", new Object[] { 1 }));
        assertEquals(3, invoker.invoke("sum", new Object[] { 1, 2 }));
    }

    @Test
    public void testNoParams() throws Exception {
        class Tester {
            public int f() { return 0; }
        }
        Tester service = new Tester();
        MethodInvoker invoker = new MethodInvoker(service);
        assertEquals(0, invoker.invoke("f", new Object[] {}));
        assertEquals(0, invoker.invoke("f", null));
    }

    @Test
    public void testTooFewParams() {
        class Tester {
            public int f(int i) { return i; }
        }
        MethodInvoker invoker = new MethodInvoker(new Tester());
        assertThrows(NoSuchMethodException.class, () -> invoker.invoke("f", new Object[] {}));
    }

    @Test
    public void testTooManyParams() {
        class Tester {
            public int f(int i) { return i; }
        }
        MethodInvoker invoker = new MethodInvoker(new Tester());
        assertThrows(NoSuchMethodException.class, () -> invoker.invoke("f", new Object[] { 1, 2 }));
    }

    @Test
    public void testNonIntefaceMethod() throws Exception {
        class Tester implements Function<String, Integer> {
            public int f(String s) { return s == null ? 0 : s.length(); }

            @Override
            public Integer apply(String s) { return f(s); }
        }
        MethodInvoker invoker = new MethodInvoker(new Tester(), Tester.class.getInterfaces());
        // invoke method in interface
        assertEquals(0, invoker.invoke("apply", new Object[]{ "" }));
        // invoke method not in interface
        assertThrows(NoSuchMethodException.class, () -> invoker.invoke("f", new Object[]{ "" }));
    }

}
