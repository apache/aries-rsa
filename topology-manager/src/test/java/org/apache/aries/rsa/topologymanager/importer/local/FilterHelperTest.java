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
package org.apache.aries.rsa.topologymanager.importer.local;

import static org.apache.aries.rsa.topologymanager.importer.local.FilterHelper.isClassExcluded;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.cm.ConfigurationAdmin;

public class FilterHelperTest {

    @Test
    public void testIgnore() {
        assertThat(isClassExcluded(ConfigurationAdmin.class.getName()), equalTo(true));
        assertThat(isClassExcluded("org.osgi.service.clusterinfo.NodeStatus"), equalTo(false));
    }

    @Test
    public void testClass() {
        testWithClassName(FilterHelperTest.class.getName());
    }

    @Test
    public void testInnerClass()  {
        testWithClassName(InnerClass.class.getName());
    }

    private void testWithClassName(String className) {
        String filter = String.format("(objectClass=%s)", className);
        String objClass = FilterHelper.getObjectClass(filter);
        Assert.assertEquals(className, objClass);
    }

    @Test
    public void testGetFullFilter() {
        String filter = "(a=b)";
        String objectClass = "my.Test";
        Assert.assertEquals(filter, FilterHelper.getFullFilter(null, filter));
        Assert.assertEquals("(objectClass=my.Test)", FilterHelper.getFullFilter(objectClass, null));
        Assert.assertEquals("(&(objectClass=my.Test)(a=b))", FilterHelper.getFullFilter(objectClass, filter));
    }

    class InnerClass {
    }

}
