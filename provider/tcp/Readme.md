# TCP Transport Provider

Allows transparent remoting using Java Serialization over TCP.
The TCP provider is very light-weight and
is ideal to get a simple remote services setup running.

## Endpoint Configuration Properties

| Key                      | Default            | Description                         |
|--------------------------|--------------------|-------------------------------------|
| service.exported.configs |                    | Must contain "aries.tcp"            |
| aries.rsa.hostname       | [autodetect]       | Hostname or IP address of service   |
| aries.rsa.bindAddress    | 0.0.0.0 (wildcard) | Address to listen on                |
| aries.rsa.port           | [free port]        | Port to listen on                   |
| aries.rsa.id             | [random id]        | Unique id string for endpoint       |
| aries.rsa.numThreads     | 10                 | Number of listener threads to spawn |
