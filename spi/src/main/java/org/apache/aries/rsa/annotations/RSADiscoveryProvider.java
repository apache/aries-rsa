package org.apache.aries.rsa.annotations;

import org.osgi.annotation.bundle.Attribute;

@org.osgi.annotation.bundle.Capability( 
        namespace = "osgi.remoteserviceadmin.discovery", 
        version = "1.1.0"
)
public @interface RSADiscoveryProvider {
   @Attribute
   public String[] protocols();
}