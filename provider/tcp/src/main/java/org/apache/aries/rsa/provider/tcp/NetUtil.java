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

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Utility methods related to networking, such as
 * getting the local address even on a Linux host
 * and SSL/TLS setup.
 */
public final class NetUtil {

    private NetUtil() {
        // Util Class
    }

    /**
     * Returns an InetAddress representing the address of the localhost. Every
     * attempt is made to find an address for this host that is not the loopback
     * address. If no other address can be found, the loopback will be returned.
     *
     * @return InetAddress the address of localhost
     * @throws UnknownHostException if there is a problem determining the address
     */
    public static InetAddress getLocalHost() throws UnknownHostException {
        InetAddress localHost = InetAddress.getLocalHost();
        if (!localHost.isLoopbackAddress()) {
            return localHost;
        }
        InetAddress[] addrs = getAllLocalUsingNetworkInterface();
        for (InetAddress addr : addrs) {
            if (!addr.isLoopbackAddress() && !addr.getHostAddress().contains(":")) {
                return addr;
            }
        }
        return localHost;
    }

    /**
     * Utility method that delegates to the methods of NetworkInterface to
     * determine addresses for this machine.
     *
     * @return all addresses found from the NetworkInterfaces
     * @throws UnknownHostException if there is a problem determining addresses
     */
    private static InetAddress[] getAllLocalUsingNetworkInterface() throws UnknownHostException {
        try {
            List<InetAddress> addresses = new ArrayList<>();
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                for (Enumeration<InetAddress> e2 = ni.getInetAddresses(); e2.hasMoreElements();) {
                    addresses.add(e2.nextElement());
                }
            }
            return addresses.toArray(new InetAddress[] {});
        } catch (SocketException ex) {
            throw new UnknownHostException("127.0.0.1");
        }
    }

    public static String getLocalIp() {
        String localIP;
        try {
            localIP = getLocalHost().getHostAddress();
        } catch (Exception e) {
            localIP = "localhost";
        }
        return localIP;
    }

    /**
     * Loads a keystore from a file.
     *
     * @param path the path to the keystore file
     * @param password the keystore password
     * @param alias an optional alias of a key in the keystore -
     *        if specified, the returned key store will only contain this single key and its certificate chain
     * @return the loaded keystore
     * @throws KeyStoreException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     */
    private static KeyStore loadKeyStore(String path, char[] password, String alias)
            throws KeyStoreException, IOException, NoSuchAlgorithmException,
                CertificateException, UnrecoverableKeyException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream stream = new FileInputStream(path)) {
            ks.load(stream, password);
        }
        // if specific key alias is provided, create a KeyStore with only the requested entry
        if (alias != null && !alias.isEmpty() && !alias.equals("*")) {
            KeyStore filtered = KeyStore.getInstance("PKCS12");
            filtered.load(null, null);
            filtered.setKeyEntry(alias, ks.getKey(alias, password), password, ks.getCertificateChain(alias));
            ks = filtered;
        }
        return ks;
    }

    /**
     * Creates an SSL context initialized with keys from the given keystore and truststore.
     *
     * @param keyStorePath the path to the keystore file (or null)
     * @param keyStorePassword the keystore password
     * @param trustStorePath the path to the truststore file (or null)
     * @param trustStorePassword the truststore password
     * @param alias an optional alias of a key in the keystore -
     *        if specified, the returned context will only contain this single key
     * @return the SSL context
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws KeyStoreException
     * @throws IOException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    public static SSLContext createSSLContext(String keyStorePath, String keyStorePassword,
            String trustStorePath, String trustStorePassword, String alias)
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException,
                IOException, UnrecoverableKeyException, KeyManagementException {
        KeyManagerFactory kmf = null;
        if (keyStorePath != null && !keyStorePath.isEmpty()) {
            kmf = KeyManagerFactory.getInstance("SunX509");
            char[] pw = keyStorePassword == null ? null : keyStorePassword.toCharArray();
            kmf.init(loadKeyStore(keyStorePath, pw, alias), pw);
        }
        TrustManagerFactory tmf = null;
        if (trustStorePath != null && !trustStorePath.isEmpty()) {
            tmf = TrustManagerFactory.getInstance("SunX509");
            char[] pw = trustStorePassword == null ? null : trustStorePassword.toCharArray();
            tmf.init(loadKeyStore(trustStorePath, pw, null));
        }
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
            kmf == null ? null : kmf.getKeyManagers(),
            tmf == null ? null : tmf.getTrustManagers(),
            null);
        return sslContext;
    }

    /**
     * Creates an SSLServerSocketFactory from the given SSL context.
     * <p>
     * This is equivalent to calling {@link SSLContext#getServerSocketFactory()},
     * but also invokes {@link SSLServerSocket#setNeedClientAuth setNeedClientAuth(true)}
     * on all created sockets.
     *
     * @param sslContext an SSL context
     * @return the SSLServerSocketFactory
     */
    public static SSLServerSocketFactory createMTLSServerSocketFactory(SSLContext sslContext) {
        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        return new SSLServerSocketFactory() {

            private ServerSocket mtls(ServerSocket ss) {
                ((SSLServerSocket)ss).setNeedClientAuth(true);
                return ss;
            }

            @Override
            public String[] getDefaultCipherSuites() {
                return ssf.getDefaultCipherSuites();
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return ssf.getSupportedCipherSuites();
            }

            @Override
            public ServerSocket createServerSocket() throws IOException {
                return mtls(ssf.createServerSocket());
            }

            @Override
            public ServerSocket createServerSocket(int port) throws IOException {
                return mtls(ssf.createServerSocket(port));
            }

            @Override
            public ServerSocket createServerSocket(int port, int backlog) throws IOException {
                return mtls(ssf.createServerSocket(port, backlog));
            }

            @Override
            public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
                return mtls(ssf.createServerSocket(port, backlog, ifAddress));
            }
        };
    }
}
