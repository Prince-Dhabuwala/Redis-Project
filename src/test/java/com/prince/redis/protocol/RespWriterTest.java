package com.prince.redis.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prince.redis.protocol.RespValue.BulkString;
import com.prince.redis.protocol.RespValue.RespArray;
import com.prince.redis.protocol.RespValue.RespInteger;
import com.prince.redis.protocol.RespValue.SimpleError;
import com.prince.redis.protocol.RespValue.SimpleString;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RespWriterTest {

    private static String encode(RespValue value) {
        return new String(RespWriter.encode(value), StandardCharsets.UTF_8);
    }

    @Test
    void simpleTypes() {
        assertEquals("+OK\r\n", encode(SimpleString.OK));
        assertEquals("-ERR bad\r\n", encode(new SimpleError("ERR bad")));
        assertEquals(":42\r\n", encode(new RespInteger(42)));
        assertEquals(":-1\r\n", encode(new RespInteger(-1)));
    }

    @Test
    void bulkStrings() {
        assertEquals("$5\r\nhello\r\n", encode(BulkString.of("hello")));
        assertEquals("$0\r\n\r\n", encode(BulkString.of("")));
        assertEquals("$-1\r\n", encode(BulkString.NULL));
    }

    @Test
    void bulkLengthCountsBytesNotChars() {
        // "é" is 2 bytes in UTF-8.
        assertEquals("$2\r\né\r\n", encode(BulkString.of("é")));
    }

    @Test
    void arrays() {
        assertEquals("*2\r\n$3\r\nfoo\r\n:7\r\n", encode(RespArray.of(BulkString.of("foo"), new RespInteger(7))));
        assertEquals("*0\r\n", encode(RespArray.of()));
        assertEquals("*-1\r\n", encode(RespArray.NULL));
    }

    @Test
    void simpleStringsCannotContainNewlines() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleString("a\r\nb"));
        assertThrows(IllegalArgumentException.class, () -> new SimpleError("bad\n"));
    }

    @Test
    void roundTripThroughParser() throws IOException {
        RespValue original = RespArray.of(
                SimpleString.OK,
                new SimpleError("ERR x"),
                new RespInteger(Long.MAX_VALUE),
                BulkString.of("line1\r\nline2"),
                BulkString.NULL,
                RespArray.of(BulkString.of("nested")),
                RespArray.NULL);
        RespParser parser = new RespParser(new ByteArrayInputStream(RespWriter.encode(original)));
        assertEquals(original, parser.read());
    }
}
