# TCP Transport Provider

Allows transparent remoting using Java Serialization over TCP.
The TCP provider is very light-weight and
is ideal to get a simple remote services setup running.

## Endpoint Configuration Properties

The exported endpoint service supports the following properties:

| Key                      | Default            | Description                         |
|--------------------------|--------------------|-------------------------------------|
| service.exported.configs |                    | Must contain "aries.tcp"            |
| aries.rsa.hostname       | [autodetect]       | Hostname or IP address of service   |
| aries.rsa.bindAddress    | 0.0.0.0 (wildcard) | Address to listen on                |
| aries.rsa.port           | [free port]        | Port to listen on                   |
| aries.rsa.id             | [random id]        | Unique id string for endpoint       |
| aries.rsa.numThreads     | 10                 | Number of listener threads to spawn |

## Provider Configuration

The provider's configuration pid is `org.apache.aries.rsa.provider.tcp`, and supports the following properties:

| Key                          | Default         | Description                                            |
|------------------------------|-----------------|--------------------------------------------------------|
| aries.rsa.keyStore           | [no server TLS] | Path to the key store file (or empty to disable TLS)   |
| aries.rsa.keyStorePassword   |                 | Key store password                                     |
| aries.rsa.trustStore         | [no client TLS] | Path to the trust store file (or empty to disable TLS) |
| aries.rsa.trustStorePassword |                 | Trust store password                                   |
| aries.rsa.keyAlias           | [autoselect]    | Alias of key within keystore to use for server TLS     |
| aries.rsa.mtls               | false           | Whether to use MTLS (require client authentication)    |
