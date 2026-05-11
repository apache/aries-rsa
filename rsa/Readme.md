# Remote Service Admin

This service is called by the Topology Manager to do the "heavy lifting" of expose local services
as remote endpoints and creating local proxy services as clients for remote endpoints.

Aries RSA has a custom [SPI DistributionProvider](https://github.com/apache/aries-rsa/blob/master/spi/src/main/java/org/apache/aries/rsa/spi/DistributionProvider.java)
that facilitates creating new transports and serializations.
The RemoteServiceAdmin bundle tracks such provider services and delegates
the actual creation of endpoints and proxies to suitable providers.

### A Note About TCK Compliance

Aries RSA runs the TCK compliance tests as part of the build process
to ensure that it is fully compliant with the RSA specification. One of
the details being tested is backwards compatibility with the
`EndpointListener` interface for legacy endpoint event producers
and consumers. This interface has been deprecated since release 1.1 of
the spec in 2015, and superceded by the newer `EndpointEventListener`
interface.

In order to be compliant with this requirement and pass the TCK tests,
this module implements an `EventListenerBridge` component to bridge the
two event types. However, since this is very unlikely to be needed in
practice (only if an OSGi container runs two different local RSA
implementations, both the modern Aries RSA implementation and another
RSA implementation that is over a decade old), and since it may have
some complexity and performance implications, this bridge is disabled
by default.

To enable the bridge for full TCK compatibility, set the system
property `org.apache.aries.rsa.bridge` to `"true"`.

Needless to say, both the legacy interface and the bridge supporting
it should be considered deprecated and may be removed in a future
release. Anyone still using them is urged to upgrade to the new interface.
