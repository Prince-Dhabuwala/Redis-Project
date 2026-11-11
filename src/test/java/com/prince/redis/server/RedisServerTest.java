package com.prince.redis.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

    private String send(String payload) throws IOException {
        try (Socket s = new Socket("localhost", server.getPort())) {
            OutputStream out = s.getOutputStream();
            out.write(payload.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            return in.readLine();
        }
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
    void unknownCommandReturnsError() throws IOException {
        assertEquals("-ERR unknown command", send("FOO\r\n"));
    }
}
