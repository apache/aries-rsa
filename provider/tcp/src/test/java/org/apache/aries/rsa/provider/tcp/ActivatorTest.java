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

import static org.easymock.EasyMock.expect;

import java.util.Dictionary;

import org.apache.aries.rsa.spi.DistributionProvider;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class ActivatorTest {

    @SuppressWarnings({
     "rawtypes", "unchecked"
    })
    @Test
    public void testStartStop() throws Exception {
        IMocksControl c = EasyMock.createControl();
        BundleContext context = c.createMock(BundleContext.class);
        ServiceRegistration sreg = c.createMock(ServiceRegistration.class);
        expect(context.registerService(EasyMock.eq(DistributionProvider.class), EasyMock.anyObject(DistributionProvider.class), EasyMock.anyObject(Dictionary.class))).andReturn(sreg );
        
        c.replay();
        Activator activator = new Activator();
        activator.start(context);
        activator.stop(context);
        c.verify();
    }
}
