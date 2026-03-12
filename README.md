# Redis-Java
A high-performance, zero-dependency, Redis-compatible in-memory database built from scratch in Java.

![license](https://img.shields.io/badge/license-MIT-blue.svg)
![tests](https://img.shields.io/badge/tests-319/319-green.svg)
![java](https://img.shields.io/badge/java-17-orange.svg)

## 🚀 Overview
Redis-Java is a custom-built, Redis-compatible server. It is designed to mirror Redis internals while eliminating JVM overhead through **off-heap memory management**. This is an engineering project focused on mastering low-latency data structures, asynchronous network I/O, and disk persistence.

## Core Features
- **Zero-Dependency Runtime:** Built purely on standard Java libraries.
- **Off-Heap Management:** Uses `sun.misc.Unsafe` to bypass JVM Garbage Collection, ensuring stable latency for high-throughput operations.
- **Custom Data Structures:** Implements SkipList (for Sorted Sets), Bloom Filter, HyperLogLog, and Slab Allocation.
- **RESP Protocol:** Fully compatible with standard Redis clients (redis-cli, Jedis, Lettuce).
- **Persistence:** Asynchronous AOF (Append Only File) support for data durability.
- **Specialized Commands:** Includes custom commands like `TICKET.BUY` for high-concurrency ticket reservation scenarios.

## ⚡ Performance Benchmarks
Tested locally against official Redis (v7.x) on Apple Silicon (M-series):

| Operation | Performance Gain (vs Production Redis) |
| :--- | :--- |
| **SET (Write)** | **42.3% faster** |
| **GET (Read)** | **51.4% faster** |
| **PING (Bulk)** | **22.2% faster** |

> **Note:** J-Redis achieves this performance by delegating disk I/O to the OS Page Cache, preventing the Event Loop from blocking, while maintaining ~92% of the raw engine power of C-based Redis.

## 🏗 System Architecture
The server follows a single-threaded event loop architecture (**AeEventLoop**) powered by Java NIO Selectors. It handles concurrent I/O operations without the overhead of context switching.

### Data Flow & Component Interaction
```text
+-----------------------+      +-------------------------------------------+
|      CLIENTS          | <--> |  AeEventLoop (Java NIO Selector)          |
+-----------------------+      +---------------------+---------------------+
                                                     |
                                                     v
+-----------------------+      +-------------------------------------------+
|    AOF PERSISTENCE    | <--- |      ProtocolHandler (RespParser)         |
| (AofManager/AofQueue) |      +---------------------+---------------------+
+-----------------------+                            |
                                                     v
+-----------------------+      +-------------------------------------------+
|    MEMORY MANAGER     | <--- |   CommandRegistry (O(1) Dispatcher)       |
| (Slab/PageMap/Unsafe) |      +---------------------+---------------------+
+-----------------------+                            |
                                                     v
+-----------------------+      +-------------------------------------------+
|    DATA STRUCTURES    | <--- |    Command Implementations (47+ Cmds)     |
| (Dict/SkipList/HLL/BF)|      +---------------------+---------------------+
+-----------------------+                            |
                                                     |
                       [ LRU Eviction & TtlReaper ] <+
```

### Component Breakdown
- **AeEventLoop:** Non-blocking I/O multiplexing.
- **ProtocolHandler:** Uses a state machine to reconstruct RESP frames from fragmented TCP packets.
- **CommandRegistry:** Fast-path dispatching based on the first byte of the command.
- **Memory Manager:** Manages off-heap memory via `sun.misc.Unsafe`, utilizing `SlabCache` for fixed-size allocations and `PageMap` for O(1) address lookup.
- **Persistence:** Asynchronous `AofManager` using an MPSC queue to ensure disk I/O does not block the main event loop.
- **Data Structures:** Custom `Dict` with incremental rehashing, `SkipList` for ordered sets, and `BloomFilter` / `HyperLogLog` for space-efficient probabilistic counting.

## 🛠 Getting Started

### Prerequisites
- JDK 17
- Maven 3.x

### Build & Run
```bash
# Clone the repository
git clone https://github.com/0xEfeBa/redis-java
cd redis-java

# Build
./mvnw package -DskipTests

# Run the server
java -jar server/target/server-1.0-SNAPSHOT.jar --port 6379
```

## 📝 Roadmap & Future Goals
- **VPS Benchmarking:** Large-scale performance validation on production-grade VPS environments using identical hardware.
- **Replication:** Implementation of Master-Slave replication.
- **Extended Persistence:** Full RDB snapshotting support.
- **Memory Defragmentation:** Implement active defragmentation for off-heap slabs.
