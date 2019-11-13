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
package org.apache.aries.rsa.itests.felix;

import java.lang.reflect.Method;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.ops4j.pax.exam.ExamSystem;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.TestContainer;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.PaxExamRuntime;

/**
 * Can be used instead of the PaxExam runner to start a second
 * OSGi container that typically hosts the server side for the test.
 * 
 * Use the @ServerConfiguration annotation to mark the config of your server side
 */
public class TwoContainerPaxExam extends PaxExam {

    private Class<?> testClass;

    public TwoContainerPaxExam(Class<?> klass) throws InitializationError {
        super(klass);
        this.testClass = klass;
    }

    @Override
    public void run(RunNotifier notifier) {
        TestContainer remoteContainer = null;
        try {
            
            ExamSystem testSystem = PaxExamRuntime.createTestSystem(remoteConfig());
            remoteContainer = PaxExamRuntime.createContainer(testSystem);
            remoteContainer.start();
            super.run(notifier);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (remoteContainer != null) {
                try {
                    remoteContainer.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
    }

    private Option[] remoteConfig() throws Exception {
        Object testO = this.testClass.newInstance();
        Method configMethod = getServerConfigMethod();
        return (Option[])configMethod.invoke(testO);
    }

    private Method getServerConfigMethod() throws NoSuchMethodException, SecurityException {
        Method[] methods = testClass.getMethods();
        for (Method method : methods) {
            if (method.getAnnotation(ServerConfiguration.class) != null) {
                if (method.getParameterTypes().length > 0) {
                    throw new IllegalArgumentException("ServerConfiguration method must have no params");
                }
                if (method.getReturnType() != Option[].class) {
                    throw new IllegalArgumentException("ServerConfiguration method must return Option[]");
                }
                return method;
            }
        }
        throw new IllegalArgumentException("One method must be annotated with @ServerConfiguration");
    }
}
