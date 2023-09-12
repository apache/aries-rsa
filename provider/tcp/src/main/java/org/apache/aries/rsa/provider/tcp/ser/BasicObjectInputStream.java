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
package org.apache.aries.rsa.provider.tcp.ser;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.Set;

import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicObjectInputStream extends ObjectInputStream {
    Logger log = LoggerFactory.getLogger(this.getClass());

    private final Set<ClassLoader> loaders = new LinkedHashSet<>(); // retains insertion order

    public BasicObjectInputStream(InputStream in, ClassLoader loader) throws IOException {
        super(in);
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                enableResolveObject(true);
                return null;
            }
        });
        loaders.add(loader); // the original classloader goes first
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        String className = desc.getName();
        ClassNotFoundException exception = null;
        for (ClassLoader loader : loaders) {
            try {
                // Must use Class.forName instead of loader.loadClass to handle cases like array of user classes
                Class<?> cls = Class.forName(className, false, loader);
                loaders.add(cls.getClassLoader()); // save transitive classloaders for other transitive classes
                return cls;
            } catch (ClassNotFoundException e) {
                if (exception == null)
                    exception = e;
                else
                    exception.addSuppressed(e);
            }
        }
        log.debug("Error loading class using classloader of user bundle. Trying our own ClassLoader now", exception);
        return super.resolveClass(desc);
    }

    @Override
    protected Object resolveObject(Object obj) throws IOException {
        if (obj instanceof VersionMarker) {
            VersionMarker versionMarker = (VersionMarker)obj;
            return Version.parseVersion(versionMarker.getVersion());
        } else if (obj instanceof DTOMarker) {
            DTOMarker dtoMarker = (DTOMarker)obj;
            ClassLoader loader = loaders.iterator().next(); // original provided classloader
            return dtoMarker.getDTO(loader);
        } else {
            return super.resolveObject(obj);
        }
    }
}
