package com.prince.redis.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Milestone 1: minimal TCP server.
 * Accepts connections (one virtual thread per client) and answers PING with +PONG.
 * Full RESP parsing arrives in Milestone 2.
 */
public class RedisServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6379;
    private static final byte[] PONG = "+PONG\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] UNKNOWN = "-ERR unknown command\r\n".getBytes(StandardCharsets.US_ASCII);

    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;

    /** Pass 0 to bind to a random free port (useful in tests). */
    public RedisServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** Blocks, accepting clients until {@link #close()} is called. */
    public void run() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                executor.submit(() -> handle(client));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void handle(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream out = client.getOutputStream()) {
            String line;
            while ((line = in.readLine()) != null) {
                // Skip RESP framing lines ("*1", "$4"); real parser comes in Milestone 2.
                if (line.isEmpty() || line.startsWith("*") || line.startsWith("$")) {
                    continue;
                }
                if (line.equalsIgnoreCase("PING")) {
                    out.write(PONG);
                } else {
                    out.write(UNKNOWN);
                }
                out.flush();
            }
        } catch (IOException e) {
            // client disconnected
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // nothing to do
        }
        executor.shutdownNow();
    }
}
