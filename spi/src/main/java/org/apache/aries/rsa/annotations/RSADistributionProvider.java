package org.apache.aries.rsa.annotations;

import org.osgi.annotation.bundle.Attribute;

@org.osgi.annotation.bundle.Capability( 
        namespace = "osgi.remoteserviceadmin.distribution", 
        version = "1.1.0"
)
public @interface RSADistributionProvider {
   @Attribute
   public String[] configs();
}