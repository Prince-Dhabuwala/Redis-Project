package com.prince.redis.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * A single RESP (REdis Serialization Protocol) value.
 * <p>
 * RESP2 has five types, each identified by its first byte:
 * <ul>
 *   <li>{@code +} simple string, e.g. {@code +OK\r\n}</li>
 *   <li>{@code -} error, e.g. {@code -ERR bad\r\n}</li>
 *   <li>{@code :} integer, e.g. {@code :42\r\n}</li>
 *   <li>{@code $} bulk string, e.g. {@code $5\r\nhello\r\n} (or {@code $-1\r\n} for null)</li>
 *   <li>{@code *} array, e.g. {@code *2\r\n...} (or {@code *-1\r\n} for null)</li>
 * </ul>
 */
public sealed interface RespValue
        permits RespValue.SimpleString, RespValue.SimpleError, RespValue.RespInteger,
                RespValue.BulkString, RespValue.RespArray {

    /** {@code +OK}. Must not contain CR or LF. */
    record SimpleString(String value) implements RespValue {
        public static final SimpleString OK = new SimpleString("OK");

        public SimpleString {
            requireSingleLine(value);
        }
    }

    /** {@code -ERR message}. Must not contain CR or LF. */
    record SimpleError(String message) implements RespValue {
        public SimpleError {
            requireSingleLine(message);
        }
    }

    /** {@code :123}. */
    record RespInteger(long value) implements RespValue {
    }

    /**
     * {@code $5\r\nhello}. Binary safe. {@code data == null} represents the null bulk string
     * ({@code $-1}), which Redis uses for "no such key".
     */
    record BulkString(byte[] data) implements RespValue {
        public static final BulkString NULL = new BulkString(null);

        public static BulkString of(String s) {
            return new BulkString(s.getBytes(StandardCharsets.UTF_8));
        }

        public boolean isNull() {
            return data == null;
        }

        /** UTF-8 view of the data, or {@code null} for the null bulk string. */
        public String asString() {
            return data == null ? null : new String(data, StandardCharsets.UTF_8);
        }

        // Records compare arrays by reference; compare contents instead.
        @Override
        public boolean equals(Object o) {
            return o instanceof BulkString other && Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return data == null ? "BulkString[null]" : "BulkString[" + asString() + "]";
        }
    }

    /** {@code *N} followed by N values. {@code elements == null} represents the null array. */
    record RespArray(List<RespValue> elements) implements RespValue {
        public static final RespArray NULL = new RespArray(null);

        public RespArray {
            elements = elements == null ? null : List.copyOf(elements);
        }

        public static RespArray of(RespValue... values) {
            return new RespArray(List.of(values));
        }

        public boolean isNull() {
            return elements == null;
        }
    }

    private static void requireSingleLine(String s) {
        if (s == null || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Simple strings/errors must be non-null and single-line");
        }
    }
}
