# Testing Log

Test steps, expected output and recorded results for every milestone.
Add a new section each milestone, and a new row in the results table each time tests are run before a commit.

## How to read this doc
- **Steps:** exact commands to run, from the project root `C:\Code_Base_Prince\Redis-Project`.
- **Expected output:** what a passing run looks like. Timings and dates will differ, but test counts must match.
- **Pass criteria:** what must be true for the milestone to count as passing.
- **Results:** a dated record of actual runs.

## Results summary

| Date | Milestone | Branch | Command | Tests run | Failures | Result |
|---|---|---|---|---|---|---|
| 2026-11-11 | 1 | Milestone1-Prince | `mvn package` | 3 | 0 | ✅ PASS |
| 2026-11-11 | 1 | Milestone1-Prince | Manual: raw RESP `PING` over TCP | n/a | n/a | ✅ PASS (`+PONG`) |
| 2026-11-11 | 2 | Milestone2-Prince | `mvn test` | 31 | 0 | ✅ PASS |
| 2026-11-11 | 2 | Milestone2-Prince | `mvn test -Dtest=RespParserTest#bulkStringIsBinarySafe` | 1 | 0 | ✅ PASS |
| 2026-11-11 | 2 | Milestone2-Prince | Manual: 3 pipelined commands over TCP | n/a | n/a | ✅ PASS |

---

## Milestone 1: Setup and TCP server

### Steps
1. Run all tests:
   ```
   mvn test
   ```
2. Manual check: start the server in terminal 1:
   ```
   mvn package
   java -jar target\redis-project-0.1.0-SNAPSHOT.jar
   ```
3. In terminal 2, send a RESP `PING` (PowerShell):
   ```powershell
   $c = New-Object Net.Sockets.TcpClient("localhost", 6379); $s = $c.GetStream()
   $b = [Text.Encoding]::ASCII.GetBytes("*1`r`n`$4`r`nPING`r`n"); $s.Write($b, 0, $b.Length)
   Start-Sleep -Milliseconds 300; $r = New-Object byte[] 256; $n = $s.Read($r, 0, 256)
   [Text.Encoding]::ASCII.GetString($r, 0, $n); $c.Close()
   ```

### Expected output
Step 1 (`mvn test`, as recorded at Milestone 1):
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.prince.redis.server.RedisServerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.068 s -- in com.prince.redis.server.RedisServerTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
Step 2:
```
Redis-Project listening on port 6379
```
Step 3:
```
+PONG
```

### Tests covered (3)
| Test | Checks |
|---|---|
| `inlinePingReturnsPong` | `PING` returns `+PONG` |
| `respPingFromRedisCliReturnsPong` | `*1 $4 PING` returns `+PONG` |
| `unknownCommandReturnsError` | `FOO` returns `-ERR unknown command` |

### Pass criteria
- `Tests run: 3, Failures: 0, Errors: 0` and `BUILD SUCCESS`
- The server replies `+PONG` to a live `PING`

> Note: Milestone 2 rewrote these server tests, so running `mvn test` on the current code shows 31 tests, not 3.

---

## Milestone 2: RESP protocol parser and writer

### Steps
1. Run all tests:
   ```
   mvn test
   ```
2. Run a single test (example):
   ```
   mvn test -Dtest=RespParserTest#bulkStringIsBinarySafe
   ```
3. Manual check: start the server in terminal 1:
   ```
   mvn package
   java -jar target\redis-project-0.1.0-SNAPSHOT.jar
   ```
4. In terminal 2, send 3 pipelined commands in one write (PowerShell):
   ```powershell
   $c = New-Object Net.Sockets.TcpClient("localhost", 6379); $s = $c.GetStream()
   $msg = "*1`r`n`$4`r`nPING`r`n*2`r`n`$4`r`nPING`r`n`$5`r`nhello`r`nFOO`r`n"
   $b = [Text.Encoding]::ASCII.GetBytes($msg); $s.Write($b, 0, $b.Length)
   Start-Sleep -Milliseconds 300; $r = New-Object byte[] 1024; $n = $s.Read($r, 0, 1024)
   [Text.Encoding]::ASCII.GetString($r, 0, $n); $c.Close()
   ```
5. Optional, with redis-cli via Docker:
   ```
   docker run --rm -it redis redis-cli -h host.docker.internal -p 6379
   ```

### Expected output
Step 1 (`mvn test`):
```
[INFO] Scanning for projects...
[INFO]
[INFO] ----------------------< com.prince:redis-project >----------------------
[INFO] Building redis-project 0.1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- resources:3.4.0:resources (default-resources) @ redis-project ---
[INFO] skip non existing resourceDirectory C:\Code_Base_Prince\Redis-Project\src\main\resources
[INFO]
[INFO] --- compiler:3.15.0:compile (default-compile) @ redis-project ---
[INFO] Nothing to compile - all classes are up to date.
[INFO]
[INFO] --- resources:3.4.0:testResources (default-testResources) @ redis-project ---
[INFO] skip non existing resourceDirectory C:\Code_Base_Prince\Redis-Project\src\test\resources
[INFO]
[INFO] --- compiler:3.15.0:testCompile (default-testCompile) @ redis-project ---
[INFO] Nothing to compile - all classes are up to date.
[INFO]
[INFO] --- surefire:3.2.5:test (default-test) @ redis-project ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO]
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.prince.redis.protocol.RespParserTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.082 s -- in com.prince.redis.protocol.RespParserTest
[INFO] Running com.prince.redis.protocol.RespWriterTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.022 s -- in com.prince.redis.protocol.RespWriterTest
[INFO] Running com.prince.redis.server.RedisServerTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.059 s -- in com.prince.redis.server.RedisServerTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.371 s
[INFO] Finished at: 2026-11-11T21:40:40+05:30
[INFO] ------------------------------------------------------------------------
```

Step 2 (single test):
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.prince.redis.protocol.RespParserTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.036 s -- in com.prince.redis.protocol.RespParserTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  1.627 s
[INFO] Finished at: 2026-11-11T21:41:24+05:30
[INFO] ------------------------------------------------------------------------
```

Step 4 (pipelined commands):
```
+PONG
$5
hello
-ERR unknown command 'FOO'
```

Step 5 (redis-cli):
```
127.0.0.1:6379> PING
PONG
127.0.0.1:6379> PING hello
"hello"
127.0.0.1:6379> FOO
(error) ERR unknown command 'FOO'
127.0.0.1:6379> PING a b
(error) ERR wrong number of arguments for 'ping' command
```

### Tests covered (31)
| Class | Count | Covers |
|---|---|---|
| `RespParserTest` | 17 | All 5 RESP types, nulls, empty values, binary safety, nested arrays, inline commands, multiple values per stream, invalid and truncated input |
| `RespWriterTest` | 6 | Encoding of every type, UTF-8 byte lengths, newline rejection, write-then-parse round trip |
| `RedisServerTest` | 8 | PING (inline, RESP, lowercase, with message), wrong arg count, unknown command, pipelining, protocol error |

### Pass criteria
- `Tests run: 31, Failures: 0, Errors: 0` and `BUILD SUCCESS`
- Each class matches its count: Parser 17, Writer 6, Server 8
- The pipelined manual check returns all 3 replies in order

---

## Template for the next milestone

````markdown
## Milestone N: <name>

### Steps
1. `mvn test`
2. <manual steps>

### Expected output
```
<paste passing output>
```

### Tests covered (<count>)
| Class | Count | Covers |
|---|---|---|

### Pass criteria
- `Tests run: <count>, Failures: 0, Errors: 0` and `BUILD SUCCESS`
````

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `mvn` is not recognized | PATH not loaded | Open a new terminal |
| `Failed to delete ...SNAPSHOT.jar` | A server is still running from the jar | Stop it (Ctrl+C, or `Stop-Process` on the java PID) |
| `Address already in use` | Port 6379 taken (old server or real Redis) | Stop it, or run with another port: `java -jar ... 6380` |
| Test failure details | n/a | See `target\surefire-reports\` |
