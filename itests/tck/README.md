# TCK Tests

This module runs the OSGi RSA TCK tests, to make sure the RSA implementation is fully compliant with the specs.

## Run the tests

To run all tests:

    ../../mvnw verify

To run a specific test:

    ../../mvnw verify -Dtck.test=<fullly.qualified.TestClass[:method]>
