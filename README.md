localsock
=========

Transparent in-memory socket implementations for localhost TCP connections within the same JVM.

Originally built in 2013 on Java 7 as a proof of concept (with mixed results), this is a total overhaul to see if the idea works better on modern Java 21+. 
_Note that this is still EXPERIMENTAL code._

Uses modern Java's SelectorProvider SPI to transparently intercept localhost socket connections, providing in-memory
implementations that bypass the OS TCP/IP stack while requiring zero changes to existing code.

Requirements
------------

* Java 21+ to run
* Maven 3 to build

Transparent Usage
-----------------

**Approach 1: Automatic via SPI (when library is on classpath)**

```java
// Standard Java NIO code - no changes needed!
ServerSocketChannel server = ServerSocketChannel.open();
server.

bind(new InetSocketAddress("localhost", 8080));

SocketChannel client = SocketChannel.open();
client.

connect(new InetSocketAddress("localhost", 8080));
// Localhost connections automatically use in-memory sockets
```

**Approach 2: System property activation**

```bash
java -Djava.nio.channels.spi.SelectorProvider=com.localsock.InMemorySelectorProvider YourApp
```

**Approach 3: Explicit API (when you need control)**

```java
// Explicit in-memory usage
ServerSocketChannel server = InMemoryChannelProvider.openInMemoryServerSocketChannel();
SocketChannel client = InMemoryChannelProvider.openInMemorySocketChannel(remoteAddress);
```

Testing
-------

Run the JUnit test suite:

```bash
mvn clean test
```

Or run the demo applications:

```bash
# Test explicit in-memory API
mvn exec:java -pl localsock-test -Dexec.mainClass="com.localsock.InMemorySocketTest"

# Test transparent interception (standard Java NIO APIs)
mvn exec:java -pl localsock-test -Dexec.mainClass="com.localsock.TransparentTest"
```
