package com.prince.redis.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisServerTest {

    private RedisServer server;

    @BeforeEach
    void start() throws IOException {
        server = new RedisServer(0);
        Thread.ofVirtual().start(server::run);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    /** Sends the payload and returns the first {@code lines} lines of the reply. */
    private List<String> send(String payload, int lines) throws IOException {
        try (Socket s = new Socket("localhost", server.getPort())) {
            s.setSoTimeout(2000);
            OutputStream out = s.getOutputStream();
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            List<String> result = new ArrayList<>();
            for (int i = 0; i < lines; i++) {
                result.add(in.readLine());
            }
            return result;
        }
    }

    private String send(String payload) throws IOException {
        return send(payload, 1).get(0);
    }

    @Test
    void inlinePingReturnsPong() throws IOException {
        assertEquals("+PONG", send("PING\r\n"));
    }

    @Test
    void respPingFromRedisCliReturnsPong() throws IOException {
        assertEquals("+PONG", send("*1\r\n$4\r\nPING\r\n"));
    }

    @Test
    void commandNamesAreCaseInsensitive() throws IOException {
        assertEquals("+PONG", send("*1\r\n$4\r\nping\r\n"));
    }

    @Test
    void pingWithMessageEchoesItAsBulkString() throws IOException {
        assertEquals(List.of("$5", "hello"), send("*2\r\n$4\r\nPING\r\n$5\r\nhello\r\n", 2));
    }

    @Test
    void pingWithTooManyArgsIsError() throws IOException {
        assertEquals("-ERR wrong number of arguments for 'ping' command", send("PING a b\r\n"));
    }

    @Test
    void unknownCommandReturnsError() throws IOException {
        assertEquals("-ERR unknown command 'FOO'", send("FOO\r\n"));
    }

    @Test
    void pipelinedCommandsAllGetReplies() throws IOException {
        String three = "*1\r\n$4\r\nPING\r\n".repeat(3);
        assertEquals(List.of("+PONG", "+PONG", "+PONG"), send(three, 3));
    }

    @Test
    void malformedInputReturnsProtocolError() throws IOException {
        assertEquals("-ERR Protocol error: invalid multibulk length", send("*-5\r\n"));
    }
}
