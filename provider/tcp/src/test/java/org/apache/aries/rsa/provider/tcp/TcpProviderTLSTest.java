package org.apache.aries.rsa.provider.tcp;

import org.apache.aries.rsa.provider.tcp.myservice.MyService;
import org.apache.aries.rsa.provider.tcp.myservice.MyServiceImpl;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.spi.ImportedService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.aries.rsa.provider.tcp.Config.PREFIX;
import static org.apache.aries.rsa.provider.tcp.TcpProviderTest.getFreePort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;

public class TcpProviderTLSTest {

    // The test keystore and truststore were created using Java's keytool.
    //
    // Create a keystore with generated private/public keys:
    //
    // keytool -genkeypair -alias myServerKey -keyalg RSA -keysize 4096
    //         -storetype PKCS12 -keystore keystore.p12 -storepass password -keypass password
    //         -dname "CN=localhost, O=test" -validity 36500
    //
    // Export the certificate (public key)
    //
    // keytool -exportcert -alias myServerKey -file server.cer
    //         -keystore keystore.p12 -storepass password
    //
    // Create a truststore with the imported certificate
    //
    // keytool -importcert -alias myServerCert -file server.cer
    //         -keystore truststore.p12 -storepass password1 -storetype PKCS12
    private static String
            KEYSTORE = TcpProviderTest.class.getResource("/keystore.p12").getPath(),
            KEYSTORE2 = TcpProviderTest.class.getResource("/keystore2.p12").getPath(),
            TRUSTSTORE = TcpProviderTest.class.getResource("/truststore.p12").getPath(),
            KEYSTORE_PASSWORD = "password",
            TRUSTSTORE_PASSWORD = "password1";

    private MyService myServiceProxy;
    private Endpoint ep;
    private ImportedService importedService;

    private void test(Map<String, Object> providerProps) throws IOException {
        Class<?>[] exportedInterfaces = new Class[] {MyService.class};
        TcpProvider provider = new TcpProvider();
        provider.activate(providerProps);
        Map<String, Object> props = new HashMap<>();
        props.put(Constants.OBJECTCLASS, new String[] {MyService.class.getName()});
        int port = getFreePort();
        props.put(Config.HOSTNAME, "localhost");
        props.put(Config.PORT, port);
        props.put(Config.ID, "service1");
        BundleContext bc = EasyMock.mock(BundleContext.class);
        ep = provider.exportService(new MyServiceImpl("service1"), bc, props, exportedInterfaces);
        assertThat(ep.description().getId(), startsWith("tcp://localhost:"));
        importedService = provider.importEndpoint(
                MyService.class.getClassLoader(),
                bc,
                exportedInterfaces,
                ep.description());
        myServiceProxy = (MyService)importedService.getService();
        assertEquals("test", myServiceProxy.echo("test"));
    }

    @After
    public void close() throws IOException {
        if (importedService != null) {
            importedService.close();
        }
        if (ep != null) {
            ep.close();
        }
    }

    @Test
    public void testNoTLS() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "keyStore", "");
        providerProps.put(PREFIX + "trustStore", null);
        providerProps.put(PREFIX + "keyStorePassword", "asdf");
        providerProps.put(PREFIX + "trustStorePassword", "asdf");
        providerProps.put(PREFIX + "keyAlias", "asdf");
        providerProps.put(PREFIX + "mtls", "false");
        test(providerProps);
    }

    @Test(expected = RuntimeException.class)
    public void testWrongPassword() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "keyStore", KEYSTORE);
        providerProps.put(PREFIX + "trustStore", TRUSTSTORE);
        providerProps.put(PREFIX + "keyStorePassword", "asdf");
        providerProps.put(PREFIX + "trustStorePassword", "asdf");
        test(providerProps);
    }

    @Test
    public void testTLS() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "keyStore", KEYSTORE);
        providerProps.put(PREFIX + "trustStore", TRUSTSTORE);
        providerProps.put(PREFIX + "keyStorePassword", KEYSTORE_PASSWORD);
        providerProps.put(PREFIX + "trustStorePassword", TRUSTSTORE_PASSWORD);
        test(providerProps);
    }

    @Test(expected = ServiceException.class)
    public void testWrongKey() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "keyStore", KEYSTORE2);
        providerProps.put(PREFIX + "trustStore", TRUSTSTORE);
        providerProps.put(PREFIX + "keyStorePassword", KEYSTORE_PASSWORD);
        providerProps.put(PREFIX + "trustStorePassword", TRUSTSTORE_PASSWORD);
        test(providerProps);
    }

    @Test
    public void testKeyAlias() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "keyAlias", "MyServerKey");
        providerProps.put(PREFIX + "keyStore", KEYSTORE);
        providerProps.put(PREFIX + "trustStore", TRUSTSTORE);
        providerProps.put(PREFIX + "keyStorePassword", KEYSTORE_PASSWORD);
        providerProps.put(PREFIX + "trustStorePassword", TRUSTSTORE_PASSWORD);
        test(providerProps);
    }

    @Test(expected = RuntimeException.class)
    public void testWrongKeyAlias() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "keyAlias", "YourService");
        providerProps.put(PREFIX + "keyStore", KEYSTORE2);
        providerProps.put(PREFIX + "trustStore", TRUSTSTORE);
        providerProps.put(PREFIX + "keyStorePassword", KEYSTORE_PASSWORD);
        providerProps.put(PREFIX + "trustStorePassword", TRUSTSTORE_PASSWORD);
        test(providerProps);
    }

    @Test(expected = RuntimeException.class)
    public void testMTLSWithoutKeyStore() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "mtls", "true");
        providerProps.put(PREFIX + "trustStore", KEYSTORE);
        test(providerProps);
    }

    @Test(expected = RuntimeException.class)
    public void testMTLSWithoutTrustStore() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "mtls", "true");
        providerProps.put(PREFIX + "trustStore", TRUSTSTORE);
        test(providerProps);
    }

    @Test
    public void testMTLS() throws IOException {
        HashMap<String, Object> providerProps = new HashMap<>();
        providerProps.put(PREFIX + "mtls", "true");
        providerProps.put(PREFIX + "keyStore", KEYSTORE);
        providerProps.put(PREFIX + "trustStore", TRUSTSTORE);
        providerProps.put(PREFIX + "keyStorePassword", KEYSTORE_PASSWORD);
        providerProps.put(PREFIX + "trustStorePassword", TRUSTSTORE_PASSWORD);
        test(providerProps);
    }

}
