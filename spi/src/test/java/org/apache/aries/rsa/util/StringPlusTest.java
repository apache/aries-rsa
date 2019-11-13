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
package org.apache.aries.rsa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class StringPlusTest {

    @Test
    public void testSplitString() {
        List<String> values = StringPlus.normalize("1, 2");
        assertEquals(2, values.size());
        assertEquals("1", values.get(0));
        assertEquals("2", values.get(1));
    }

    @Test
    public void testNormalizeStringPlus() {
        String s1 = "s1";
        String s2 = "s2";
        String s3 = "s3";
        List<String> sa = Arrays.asList(s1, s2, s3);
        Collection<Object> sl = Arrays.asList(s1, s2, s3, new Object()); // must be skipped
        assertNull(StringPlus.normalize(new Object()));
        assertEquals(Collections.singletonList(s1), StringPlus.normalize(s1));
        assertEquals(sa, StringPlus.normalize(sa));
        assertEquals(sa, StringPlus.normalize(sl));
    }

}
