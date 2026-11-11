package com.prince.redis.server;

import com.prince.redis.protocol.RespParser;
import com.prince.redis.protocol.RespProtocolException;
import com.prince.redis.protocol.RespValue;
import com.prince.redis.protocol.RespValue.BulkString;
import com.prince.redis.protocol.RespValue.RespArray;
import com.prince.redis.protocol.RespValue.SimpleError;
import com.prince.redis.protocol.RespValue.SimpleString;
import com.prince.redis.protocol.RespWriter;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP server speaking RESP.
 * <p>
 * One virtual thread per client. Each request is parsed with {@link RespParser} and each
 * reply written with {@link RespWriter}. Milestone 2 supports {@code PING}; a proper
 * command dispatcher arrives in Milestone 3.
 */
public class RedisServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6379;
    private static final SimpleString PONG = new SimpleString("PONG");

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
        try (client) {
            RespParser parser = new RespParser(client.getInputStream());
            RespWriter writer = new RespWriter(client.getOutputStream());
            try {
                RespValue request;
                while ((request = parser.read()) != null) {
                    writer.write(execute(request));
                    // Pipelining: only flush once the client's queued commands are all answered.
                    if (!parser.hasBufferedInput()) {
                        writer.flush();
                    }
                }
            } catch (RespProtocolException e) {
                // Same behaviour as Redis: report the error, then drop the connection.
                writer.write(new SimpleError("ERR Protocol error: " + e.getMessage()));
                writer.flush();
            }
        } catch (IOException e) {
            // client disconnected
        }
    }

    /** Turns a parsed request into a reply. */
    RespValue execute(RespValue request) {
        List<String> args = toArgs(request);
        if (args == null) {
            return new SimpleError("ERR Protocol error: expected array of bulk strings");
        }
        String name = args.get(0).toUpperCase(Locale.ROOT);
        return switch (name) {
            case "PING" -> ping(args);
            default -> new SimpleError("ERR unknown command '" + args.get(0) + "'");
        };
    }

    private static RespValue ping(List<String> args) {
        return switch (args.size()) {
            case 1 -> PONG;
            case 2 -> BulkString.of(args.get(1));
            default -> new SimpleError("ERR wrong number of arguments for 'ping' command");
        };
    }

    /** A command is a non-empty array of non-null bulk strings. Returns null otherwise. */
    private static List<String> toArgs(RespValue request) {
        if (!(request instanceof RespArray array) || array.isNull() || array.elements().isEmpty()) {
            return null;
        }
        List<String> args = new ArrayList<>(array.elements().size());
        for (RespValue element : array.elements()) {
            if (!(element instanceof BulkString bulk) || bulk.isNull()) {
                return null;
            }
            args.add(bulk.asString());
        }
        return args;
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
