package com.prince.redis.protocol;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads RESP values from a byte stream.
 * <p>
 * Supports all five RESP2 types plus "inline" commands (plain text such as {@code PING}
 * typed into telnet), which are returned as an array of bulk strings, just like real Redis.
 * <p>
 * Not thread-safe: use one parser per connection.
 */
public final class RespParser {

    /** Same limits as real Redis defaults. */
    static final int MAX_BULK_LENGTH = 512 * 1024 * 1024;
    static final int MAX_ARRAY_LENGTH = 1024 * 1024;
    static final int MAX_LINE_LENGTH = 64 * 1024;
    private static final int MAX_DEPTH = 32;

    private final InputStream in;

    public RespParser(InputStream in) {
        this.in = in instanceof BufferedInputStream ? in : new BufferedInputStream(in);
    }

    /**
     * Reads the next value.
     *
     * @return the value, or {@code null} if the stream ended cleanly before a new value started
     * @throws RespProtocolException if the input is not valid RESP
     * @throws EOFException if the stream ends in the middle of a value
     */
    public RespValue read() throws IOException {
        while (true) {
            int first = in.read();
            if (first == -1) {
                return null;
            }
            if (first == '\r' || first == '\n') {
                continue; // tolerate blank lines between commands
            }
            if (isTypePrefix(first)) {
                return readValue(first, 0);
            }
            RespValue inline = readInline(first);
            if (inline != null) {
                return inline;
            }
        }
    }

    /** True if more bytes are already available, i.e. the client pipelined several commands. */
    public boolean hasBufferedInput() throws IOException {
        return in.available() > 0;
    }

    private RespValue readValue(int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new RespProtocolException("nesting too deep");
        }
        return switch (type) {
            case '+' -> new RespValue.SimpleString(readLine());
            case '-' -> new RespValue.SimpleError(readLine());
            case ':' -> new RespValue.RespInteger(parseLong(readLine()));
            case '$' -> readBulkString();
            case '*' -> readArray(depth);
            default -> throw new RespProtocolException("expected type byte, got '" + (char) type + "'");
        };
    }

    private RespValue readBulkString() throws IOException {
        long length = parseLong(readLine());
        if (length == -1) {
            return RespValue.BulkString.NULL;
        }
        if (length < 0 || length > MAX_BULK_LENGTH) {
            throw new RespProtocolException("invalid bulk length");
        }
        byte[] data = in.readNBytes((int) length);
        if (data.length != length) {
            throw new EOFException("stream ended inside bulk string");
        }
        expectCrlf();
        return new RespValue.BulkString(data);
    }

    private RespValue readArray(int depth) throws IOException {
        long count = parseLong(readLine());
        if (count == -1) {
            return RespValue.RespArray.NULL;
        }
        if (count < 0 || count > MAX_ARRAY_LENGTH) {
            throw new RespProtocolException("invalid multibulk length");
        }
        List<RespValue> elements = new ArrayList<>((int) count);
        for (int i = 0; i < count; i++) {
            int type = in.read();
            if (type == -1) {
                throw new EOFException("stream ended inside array");
            }
            elements.add(readValue(type, depth + 1));
        }
        return new RespValue.RespArray(elements);
    }

    /** Inline command: whitespace separated words on one line. Returns null for a blank line. */
    private RespValue readInline(int first) throws IOException {
        String line = (char) first + readLine();
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<RespValue> args = new ArrayList<>();
        for (String word : trimmed.split("\\s+")) {
            args.add(RespValue.BulkString.of(word));
        }
        return new RespValue.RespArray(args);
    }

    /** Reads up to the next LF, dropping a trailing CR. */
    private String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(32);
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new EOFException("stream ended inside line");
            }
            if (b == '\n') {
                break;
            }
            if (buf.size() >= MAX_LINE_LENGTH) {
                throw new RespProtocolException("line too long");
            }
            buf.write(b);
        }
        byte[] bytes = buf.toByteArray();
        int len = bytes.length;
        if (len > 0 && bytes[len - 1] == '\r') {
            len--;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    private void expectCrlf() throws IOException {
        int cr = in.read();
        int lf = in.read();
        if (cr == -1 || lf == -1) {
            throw new EOFException("stream ended before CRLF");
        }
        if (cr != '\r' || lf != '\n') {
            throw new RespProtocolException("expected CRLF after bulk data");
        }
    }

    private static long parseLong(String s) throws RespProtocolException {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new RespProtocolException("invalid integer '" + s + "'");
        }
    }

    private static boolean isTypePrefix(int b) {
        return b == '+' || b == '-' || b == ':' || b == '$' || b == '*';
    }
}
