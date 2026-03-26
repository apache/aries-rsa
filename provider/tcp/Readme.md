# TCP Transport Provider

Allows transparent remoting using Java Serialization over TCP.
The TCP provider is very light-weight and
is ideal to get a simple remote services setup running.

## Endpoint Configuration Properties

The exported endpoint service supports the following properties:

| Key                      | Default            | Description                         |
|--------------------------|--------------------|-------------------------------------|
| service.exported.configs |                    | Must contain "aries.tcp"            |
| aries.tcp.hostname       | [autodetect]       | Hostname or IP address of service   |
| aries.tcp.bindAddress    | 0.0.0.0 (wildcard) | Address to listen on                |
| aries.tcp.port           | [free port]        | Port to listen on                   |
| aries.tcp.id             | [random id]        | Unique id string for endpoint       |
| aries.tcp.numThreads     | 10                 | Number of listener threads to spawn |

## Provider Configuration

The provider's configuration pid is `org.apache.aries.rsa.provider.tcp`, and supports the following properties:

| Key                          | Default         | Description                                            |
|------------------------------|-----------------|--------------------------------------------------------|
| aries.tcp.keyStore           | [no server TLS] | Path to the key store file (or empty to disable TLS)   |
| aries.tcp.keyStorePassword   |                 | Key store password                                     |
| aries.tcp.trustStore         | [no client TLS] | Path to the trust store file (or empty to disable TLS) |
| aries.tcp.trustStorePassword |                 | Trust store password                                   |
| aries.tcp.keyAlias           | [autoselect]    | Alias of key within keystore to use for server TLS     |
| aries.tcp.mtls               | false           | Whether to use MTLS (require client authentication)    |
