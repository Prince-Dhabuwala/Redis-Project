package com.prince.redis;

import com.prince.redis.server.RedisServer;

import java.io.IOException;

/** Entry point. Usage: java -jar redis-project.jar [port] */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : RedisServer.DEFAULT_PORT;
        RedisServer server = new RedisServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("Redis-Project listening on port " + server.getPort());
        server.run();
    }
}
