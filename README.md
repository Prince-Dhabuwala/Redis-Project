# Redis-Project
Making my Own redis with a greater purpose towards the end

**Language:** Java 21 | **Build:** Maven | **Tests:** JUnit 5 | **Port:** 6379

## Rules
- Every commit updates this README.
- When a milestone is worked on, update its **Status** and **Comments** in the tracker below.
- Add a detailed section for it in [Milestone Details](#milestone-details).
- Update [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md) with any new components and the milestone log.

- Run tests before every commit and record the result in [docs/TESTING.md](docs/TESTING.md).

How the code works: see [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).
How to test and past results: see [docs/TESTING.md](docs/TESTING.md).

Status legend: ⬜ Not started | 🟡 In progress | ✅ Done

## Milestone Tracker

| # | Milestone | Demo outcome | Status | Comments |
|---|-----------|--------------|--------|----------|
| 1 | Setup and TCP server | `redis-cli PING` returns `PONG` | ✅ | Maven + Java 21 scaffold, virtual-thread TCP server, PING works inline and via RESP, 3 tests passing |
| 2 | RESP protocol parser and writer | Unit tests pass, real `redis-cli` input parsed | ✅ | Full RESP2 parser/writer (all 5 types, null, binary-safe, inline), pipelining, protocol errors, `PING [msg]`, 31 tests passing |
| 3 | Command dispatcher, `ECHO`/`SET`/`GET` | `SET a 1` then `GET a` | ⬜ | |
| 4 | Concurrency (virtual threads, thread-safe store) | Many clients at once, stress test | ⬜ | |
| 5 | Expiry (`SET EX/PX`, `EXPIRE`, `TTL`, `PERSIST`) | Key disappears after N seconds | ⬜ | |
| 6 | More string commands (`DEL`, `EXISTS`, `INCR`, `MSET`, `KEYS`, `TYPE`) | Counter and cache demo | ⬜ | |
| 7 | Lists (`LPUSH`, `RPUSH`, `LPOP`, `LRANGE`) | Simple job queue | ⬜ | |
| 8 | Hashes and Sets | User profiles and tags | ⬜ | |
| 9 | Sorted sets (skip list) | Leaderboard | ⬜ | |
| 10 | AOF persistence | Restart and data is still there | ⬜ | |
| 11 | RDB snapshots and AOF rewrite | Faster startup, smaller log | ⬜ | |
| 12 | Pub/Sub | Chat across two terminals | ⬜ | |
| 13 | Transactions (`MULTI`/`EXEC`/`DISCARD`) | Atomic transfer demo | ⬜ | |
| 14 | Eviction (`maxmemory`, LRU/LFU) | Memory cap evicts old keys | ⬜ | |
| 15 | NIO event loop | Compare with virtual-thread version | ⬜ | |
| 16 | Replication (leader and follower) | Write to leader, read from follower | ⬜ | |
| 17 | Benchmarking and optimization | Ops/sec table vs real Redis | ⬜ | |
| 18 | Config, logging, `INFO` and metrics | Production-style polish | ⬜ | |
| 19 | Security (`AUTH`, optional TLS) | Password-protected server | ⬜ | |
| 20 | Docker, CI and release | `docker run` demo, green pipeline | ⬜ | |

Stretch goals: Streams, clustering and sharding, scripting, Bitmaps and HyperLogLog.

## Milestone Details

Copy this template for each milestone as it is completed:

```markdown
### Milestone N: <name>
- **Points covered:**
- **Use case:**
- **What it does:**
- **How to run/test:**
- **Comments:**
- **Next task:**
```

### Milestone 1: Setup and TCP server
- **Points covered:**
  - Maven project (`pom.xml`, Java 21, JUnit 5), `.gitignore`
  - `RedisServer`: `ServerSocket` on port 6379, one virtual thread per client
  - `PING` answered with `+PONG` (inline form and RESP form sent by `redis-cli`)
  - Unknown commands answered with `-ERR unknown command`
  - `Main` entry point with optional port argument and clean shutdown hook
- **Use case:** The foundation every later feature plugs into. Proves a client can connect and talk to our server.
- **What it does:** Listens on TCP, reads each line a client sends, replies `+PONG` to `PING`. RESP framing lines (`*1`, `$4`) are skipped for now.
- **How to run/test:**
  ```
  mvn package                                         # build + run tests
  java -jar target/redis-project-0.1.0-SNAPSHOT.jar   # start on 6379 (or pass a port)
  redis-cli PING                                      # -> PONG
  ```
- **Comments:** Line-based handling is a temporary shortcut. It will be replaced by a real RESP parser.
- **Next task:** Milestone 2, build a proper RESP parser and writer (simple strings, errors, integers, bulk strings, arrays) with unit tests.

### Milestone 2: RESP protocol parser and writer
- **Points covered:**
  - `RespValue`: sealed interface with the 5 RESP2 types (`SimpleString`, `SimpleError`, `RespInteger`, `BulkString`, `RespArray`), including null bulk string (`$-1`) and null array (`*-1`)
  - `RespParser`: reads values from a byte stream. Binary safe (length-prefixed bulk strings), nested arrays, inline commands (`PING` typed in telnet), size limits like real Redis (512 MB bulk, 1M array items)
  - `RespWriter`: serializes any `RespValue`, buffered output, `encode()` helper for tests and future AOF
  - `RespProtocolException` for malformed input: server replies `-ERR Protocol error: ...` and closes the connection, like Redis
  - `RedisServer` now uses the parser and writer instead of line reading
  - Pipelining: several commands sent at once are all answered, with a single flush
  - `PING` (`+PONG`), `PING msg` (echoes msg), case-insensitive command names, real Redis error text for unknown commands and wrong arg counts
  - 31 tests: `RespParserTest` (17), `RespWriterTest` (6), `RedisServerTest` (8)
- **Use case:** Every Redis client in every language speaks RESP. With a correct parser, any of them (`redis-cli`, Jedis, StackExchange.Redis, redis-py) can talk to our server.
- **What it does:** Turns raw bytes like `*2\r\n$4\r\nPING\r\n$5\r\nhello\r\n` into `["PING", "hello"]`, runs the command, and encodes the reply (`$5\r\nhello\r\n`) back to bytes.
- **How to run/test:**
  ```
  mvn package                                         # build + run all 31 tests
  java -jar target/redis-project-0.1.0-SNAPSHOT.jar
  redis-cli PING                                      # -> PONG
  redis-cli PING hello                                # -> "hello"
  redis-cli FOO                                       # -> (error) ERR unknown command 'FOO'
  ```
- **Comments:** Commands are still a small `switch` inside `RedisServer`. Values are decoded to `String` for now, so the store will be binary safe only once we keep `byte[]` in Milestone 3.
- **Next task:** Milestone 3, add a command dispatcher (`Command` interface plus a registry) and an in-memory key-value store, then implement `ECHO`, `SET`, `GET`.
