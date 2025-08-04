localsock
=========

_Note that this is EXPERIMENTAL code._

Transparent in-memory socket implementations for localhost TCP connections within the same JVM.

Originally built in 2013 on Java 7 as a proof of concept (with mixed results), this is a total overhaul to see if the idea works better on modern Java 21+. 

So far, benchmarks show that this is actually _slower_ than going through the OS. The idea was that staying in the JVM might save some overhead - but it looks like we can't beat the decades-long perf optimisation that has gone into the kernel's networking infrastructure.

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
server.bind(new InetSocketAddress("localhost", 8080));

SocketChannel client = SocketChannel.open();
client.connect(new InetSocketAddress("localhost", 8080));
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

Run the standard test suite (fast):

```bash
mvn clean test
```

Run performance tests (requires explicit activation):

```bash
mvn clean test -Pperformance
```

Or run the demo applications:

```bash
# Test explicit in-memory API
mvn exec:java -pl localsock-test -Dexec.mainClass="com.localsock.InMemorySocketTest"

# Test transparent interception (standard Java NIO APIs)
mvn exec:java -pl localsock-test -Dexec.mainClass="com.localsock.TransparentTest"
```

Performance Testing
-------------------

Run benchmarks to compare in-memory vs network socket performance:

```bash
# Full performance benchmark (proper JVM warmup, ~5 minutes)
mvn exec:java -pl localsock-benchmark -Dexec.mainClass="com.localsock.benchmark.PerformanceBenchmark"

# Stress test with many concurrent connections  
mvn exec:java -pl localsock-benchmark -Dexec.mainClass="com.localsock.benchmark.StressBenchmark"

# Performance-related JUnit tests
mvn test -pl localsock-benchmark -Pperformance

# All tests across all modules (excludes performance tests by default)
mvn clean test
```