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
package org.apache.aries.rsa.provider.fastbin.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.HashMap;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ClassLoaderObjectInputStream extends ObjectInputStream {

    /** <p>Maps primitive type names to corresponding class objects.</p> */
    private static final HashMap<String, Class> primClasses = new HashMap<>(8, 1.0F);

    private ClassLoader classLoader;

    public ClassLoaderObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    protected Class resolveClass(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {
        return load(classDesc.getName());
    }

    protected Class resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
        Class[] cinterfaces = new Class[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            cinterfaces[i] = load(interfaces[i]);
        }

        try {
            return Proxy.getProxyClass(classLoader, cinterfaces);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(null, e);
        }
    }

    private Class load(String className)
            throws ClassNotFoundException {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            final Class clazz = primClasses.get(className);
            if (clazz != null) {
                return clazz;
            } else {
                try{
                    //try to load it with our own classloader (could be e.g. a service exception)
                    return Class.forName(className, false, getClass().getClassLoader());
                } catch(ClassNotFoundException e2) {
                    //ignore
                }
                throw e;
            }
        }
    }

    static {
        primClasses.put("boolean", boolean.class);
        primClasses.put("byte", byte.class);
        primClasses.put("char", char.class);
        primClasses.put("short", short.class);
        primClasses.put("int", int.class);
        primClasses.put("long", long.class);
        primClasses.put("float", float.class);
        primClasses.put("double", double.class);
        primClasses.put("void", void.class);
    }
}
