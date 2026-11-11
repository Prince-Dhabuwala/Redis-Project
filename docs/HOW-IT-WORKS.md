# How It Works

Short guide to how this Redis clone is built. Updated every milestone.

## 1. Request flow

```
client ──bytes──► RedisServer ──► RespParser ──► execute() ──► RespWriter ──bytes──► client
       *2 $4 PING $5 hello      ["PING","hello"]   BulkString("hello")   $5\r\nhello\r\n
```

1. `RedisServer` accepts a TCP connection and gives it its own virtual thread.
2. `RespParser` turns the incoming bytes into a `RespValue`.
3. `execute()` runs the command and returns a `RespValue` reply.
4. `RespWriter` turns the reply back into bytes and sends them.

## 2. Project layout

```
src/main/java/com/prince/redis/
├── Main.java                  entry point, reads port (default 6379)
├── server/RedisServer.java    sockets, threads, command execution
└── protocol/
    ├── RespValue.java         the 5 RESP types as records
    ├── RespParser.java        bytes → RespValue
    ├── RespWriter.java        RespValue → bytes
    └── RespProtocolException  bad input from client
src/test/java/...              JUnit 5 tests, same packages
```

## 3. RESP cheat sheet

| Prefix | Type | Example | Java type |
|---|---|---|---|
| `+` | Simple string | `+OK\r\n` | `SimpleString` |
| `-` | Error | `-ERR bad\r\n` | `SimpleError` |
| `:` | Integer | `:42\r\n` | `RespInteger` |
| `$` | Bulk string | `$5\r\nhello\r\n`, null `$-1\r\n` | `BulkString` (`byte[]`) |
| `*` | Array | `*2\r\n...`, null `*-1\r\n` | `RespArray` |

Clients always send commands as an array of bulk strings.

## 4. Key components

**RespValue**
- `sealed` interface, so the compiler knows all 5 types and a `switch` needs no `default`.
- `BulkString` holds `byte[]` (binary safe). `null` data means RESP null.

**RespParser**
- Reads the first byte, then dispatches on the type.
- Bulk strings read exactly `length` bytes, so values can contain `\r\n`.
- Arrays are recursive, with a depth limit of 32.
- Inline commands (`PING` typed in telnet) become the same array format.
- Limits: 512 MB bulk, 1M array items, 64 KB lines.
- `RespProtocolException` means bad input. `EOFException` means the client disconnected mid-value.

**RespWriter**
- Mirror of the parser. Output is buffered and sent on `flush()`.
- Bulk length counts bytes, not characters (`é` is `$2`).
- `encode(value)` returns bytes directly (tests, later AOF).

**RedisServer**
- `run()`: accept loop, one virtual thread per client.
- `handle()`: read → execute → write, until the client disconnects.
- Pipelining: flush only when no more input is buffered, so many replies go in one send.
- Protocol error: reply `-ERR Protocol error: ...`, then close (same as Redis).
- `execute()`: uppercases the command name (`Locale.ROOT`) and dispatches.

## 5. Design decisions

| Decision | Why |
|---|---|
| Java 21 virtual threads | Simple blocking code that still scales to thousands of clients |
| Sealed interface plus records | Type-safe, immutable values, compiler-checked switches |
| `byte[]` for bulk strings | Redis values are binary safe |
| Buffered I/O plus smart flush | Fewer network writes, supports pipelining |
| Size and depth limits | Protects against malicious or broken clients |
| Real Redis error messages | Existing clients and tools behave correctly |

## 6. Build and run

```
mvn package                                         # compile + run tests + build jar
java -jar target/redis-project-0.1.0-SNAPSHOT.jar   # start on 6379 (or pass a port)
redis-cli PING                                      # -> PONG
```

## 7. Milestone log

| # | What was added | Key files |
|---|---|---|
| 1 | Maven project, TCP server, `PING` | `pom.xml`, `Main`, `RedisServer` |
| 2 | Full RESP parser and writer, pipelining, protocol errors, `PING [msg]` | `protocol/*` |

## 8. Coming next (Milestone 3)

- `Command` interface plus a registry, replacing the `switch` in `execute()`.
- In-memory store (`byte[]` keys and values).
- `ECHO`, `SET`, `GET`.
