package com.prince.redis.protocol;

import java.io.IOException;

/** Thrown when a client sends bytes that are not valid RESP. */
public class RespProtocolException extends IOException {

    public RespProtocolException(String message) {
        super(message);
    }
}
