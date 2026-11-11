package com.prince.redis.protocol;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Serializes RESP values to a byte stream.
 * <p>
 * Output is buffered: call {@link #flush()} to send it. Not thread-safe.
 */
public final class RespWriter {

    private static final byte[] CRLF = {'\r', '\n'};

    private final OutputStream out;

    public RespWriter(OutputStream out) {
        this.out = out instanceof BufferedOutputStream ? out : new BufferedOutputStream(out);
    }

    public void write(RespValue value) throws IOException {
        switch (value) {
            case RespValue.SimpleString s -> line('+', s.value());
            case RespValue.SimpleError e -> line('-', e.message());
            case RespValue.RespInteger i -> line(':', Long.toString(i.value()));
            case RespValue.BulkString b -> {
                if (b.isNull()) {
                    line('$', "-1");
                } else {
                    line('$', Integer.toString(b.data().length));
                    out.write(b.data());
                    out.write(CRLF);
                }
            }
            case RespValue.RespArray a -> {
                if (a.isNull()) {
                    line('*', "-1");
                } else {
                    line('*', Integer.toString(a.elements().size()));
                    for (RespValue element : a.elements()) {
                        write(element);
                    }
                }
            }
        }
    }

    public void flush() throws IOException {
        out.flush();
    }

    /** Encodes a value to bytes. Handy for tests and, later, for the append-only file. */
    public static byte[] encode(RespValue value) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        RespWriter writer = new RespWriter(buf);
        try {
            writer.write(value);
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // cannot happen with an in-memory stream
        }
        return buf.toByteArray();
    }

    private void line(char prefix, String text) throws IOException {
        out.write(prefix);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
    }
}
