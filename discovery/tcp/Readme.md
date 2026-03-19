# TCP Discovery

Discovers remote endpoints using direct TCP connections between all peers.
This is useful for small clusters of local peers such as a few hosts on a LAN
or even a few framework instances on the same host (e.g. two-container intergration tests).

* Every peer opens a server socket on its configured bind address and port.
* Every peer is configured with a fixed list of peer addresses.
* Every peer opens a TCP connection to every other known peer (one per pair).
* Discovery messages are sent directly between the peers.
* All messages are serialized using Java serialization, so there are no external dependencies.
* Broken connections are retried in a loop with a configurable delay.
* An optional basic gossip protocol is supported: every connection handshake includes
  the addresses of all peers known to each side, and new connections are opened to any previously
  unknown ones. The configured addresses are thus considered 'seed' addresses to join the peer group.

## Discovery Configuration

PID: org.apache.aries.rsa.discovery.tcp

| Key            | Default                     | Description                                                                        |
|----------------|-----------------------------|------------------------------------------------------------------------------------|
| address        | localhost:7667              | adress and port to publish to peers<br/>If port is not specified, default is used. |
| bindAddress    | 0.0.0.0<br>(all interfaces) | The address to bind the server socket to                                           |
| peers          |                             | Comma-separated list of peer addresses (host:port)                                 |
| reconnectDelay | 5000                        | Delay (in millis) between failed connection retries                                |
| gossip         | true                        | Enable the basic gossip protocol                                                   |

In order to facilitate configuration in test environments, configuration properties that
are not defined via Configuration Admin (with the above PID) fall back to framework
properties, and then to system properties. These framework/system properties are specified using the PID
as the property name prefix.

For example, in an integration test setup with two frameworks, each instance can
specify its own distinct port using a property such as
`org.apache.aries.rsa.discovery.tcp.address=localhost:7668`
and specify each other as peers using
`org.apache.aries.rsa.discovery.tcp.peers=localhost:7669`.
