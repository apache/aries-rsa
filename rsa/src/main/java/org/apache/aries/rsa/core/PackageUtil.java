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
package org.apache.aries.rsa.core;

import org.osgi.framework.*;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Utility methods for obtaining package metadata.
 */
public class PackageUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PackageUtil.class);

    protected static Function<Class<?>, Bundle> BUNDLE_FINDER = FrameworkUtil::getBundle;

    /**
     * Tries to retrieve the version of iClass via its bundle exported packages metadata.
     *
     * @param iClass tThe interface for which the version should be found
     * @return the version of the interface or "0.0.0" if no version information could be found or an error
     *         occurred during the retrieval
     */
    public static String getVersion(Class<?> iClass) {
        Bundle bundle = BUNDLE_FINDER.apply(iClass);
        if (bundle != null) {
            BundleWiring wiring = bundle.adapt(BundleWiring.class);
            List<BundleCapability> capabilities = wiring.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);
            LOG.debug("Interface {} found in bundle {} with exports {}", iClass.getName(), bundle.getSymbolicName(), capabilities);
            if (capabilities != null) {
                String iPackage = iClass.getPackage().getName();
                LOG.debug("Looking for exported package: {}", iPackage);
                for (BundleCapability cap : capabilities) {
                    String capPackage = (String)cap.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE);
                    if (iPackage.equals(capPackage)) {
                        Version version = (Version)cap.getAttributes().get(PackageNamespace.CAPABILITY_VERSION_ATTRIBUTE);
                        LOG.debug("found package {} version {}", iPackage, version);
                        if (version != null)
                            return version.toString();
                    }
                }
            }
        }

        LOG.info("Unable to find interface version for interface {}. Falling back to 0.0.0", iClass.getName());
        return "0.0.0";
    }
}
