package com.prince.redis.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prince.redis.protocol.RespValue.BulkString;
import com.prince.redis.protocol.RespValue.RespArray;
import com.prince.redis.protocol.RespValue.RespInteger;
import com.prince.redis.protocol.RespValue.SimpleError;
import com.prince.redis.protocol.RespValue.SimpleString;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RespParserTest {

    private static RespParser parser(String input) {
        return new RespParser(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static RespValue parse(String input) throws IOException {
        return parser(input).read();
    }

    @Test
    void simpleString() throws IOException {
        assertEquals(new SimpleString("OK"), parse("+OK\r\n"));
    }

    @Test
    void error() throws IOException {
        assertEquals(new SimpleError("ERR something bad"), parse("-ERR something bad\r\n"));
    }

    @Test
    void integers() throws IOException {
        assertEquals(new RespInteger(1000), parse(":1000\r\n"));
        assertEquals(new RespInteger(-42), parse(":-42\r\n"));
    }

    @Test
    void bulkString() throws IOException {
        assertEquals(BulkString.of("hello"), parse("$5\r\nhello\r\n"));
    }

    @Test
    void emptyBulkString() throws IOException {
        assertEquals(BulkString.of(""), parse("$0\r\n\r\n"));
    }

    @Test
    void nullBulkString() throws IOException {
        assertEquals(BulkString.NULL, parse("$-1\r\n"));
    }

    @Test
    void bulkStringIsBinarySafe() throws IOException {
        // CRLF inside the payload must not end the value: the length prefix decides.
        assertEquals(BulkString.of("a\r\nb"), parse("$4\r\na\r\nb\r\n"));

        byte[] binary = {0, (byte) 0xFF, '\r', '\n', 7};
        byte[] input = new byte[4 + binary.length + 2];
        System.arraycopy("$5\r\n".getBytes(StandardCharsets.US_ASCII), 0, input, 0, 4);
        System.arraycopy(binary, 0, input, 4, binary.length);
        input[input.length - 2] = '\r';
        input[input.length - 1] = '\n';
        RespValue value = new RespParser(new ByteArrayInputStream(input)).read();
        assertArrayEquals(binary, ((BulkString) value).data());
    }

    @Test
    void commandArrayAsSentByRedisCli() throws IOException {
        RespValue value = parse("*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n");
        assertEquals(RespArray.of(BulkString.of("SET"), BulkString.of("key"), BulkString.of("value")), value);
    }

    @Test
    void emptyAndNullArrays() throws IOException {
        assertEquals(RespArray.of(), parse("*0\r\n"));
        assertEquals(RespArray.NULL, parse("*-1\r\n"));
    }

    @Test
    void nestedMixedArray() throws IOException {
        RespValue value = parse("*3\r\n:1\r\n*2\r\n+a\r\n$-1\r\n-ERR x\r\n");
        assertEquals(RespArray.of(
                new RespInteger(1),
                RespArray.of(new SimpleString("a"), BulkString.NULL),
                new SimpleError("ERR x")), value);
    }

    @Test
    void inlineCommand() throws IOException {
        assertEquals(RespArray.of(BulkString.of("PING")), parse("PING\r\n"));
        assertEquals(RespArray.of(BulkString.of("SET"), BulkString.of("a"), BulkString.of("1")),
                parse("  SET   a 1 \n"));
    }

    @Test
    void multipleValuesInOneStream() throws IOException {
        RespParser p = parser("+A\r\n\r\n:2\r\nPING\r\n");
        assertEquals(new SimpleString("A"), p.read());
        assertEquals(new RespInteger(2), p.read());
        assertEquals(RespArray.of(BulkString.of("PING")), p.read());
        assertNull(p.read(), "clean end of stream returns null");
    }

    @Test
    void emptyStreamReturnsNull() throws IOException {
        assertNull(parse(""));
    }

    @Test
    void invalidIntegerIsProtocolError() {
        RespProtocolException e = assertThrows(RespProtocolException.class, () -> parse(":abc\r\n"));
        assertTrue(e.getMessage().contains("invalid integer"));
    }

    @Test
    void invalidLengthsAreProtocolErrors() {
        assertThrows(RespProtocolException.class, () -> parse("$-5\r\n"));
        assertThrows(RespProtocolException.class, () -> parse("*-2\r\n"));
        assertThrows(RespProtocolException.class, () -> parse("$x\r\n"));
    }

    @Test
    void missingCrlfAfterBulkIsProtocolError() {
        assertThrows(RespProtocolException.class, () -> parse("$3\r\nabcXY"));
    }

    @Test
    void truncatedInputIsEof() {
        assertThrows(EOFException.class, () -> parse("$10\r\nshort\r\n"));
        assertThrows(EOFException.class, () -> parse("*2\r\n$1\r\na\r\n"));
        assertThrows(EOFException.class, () -> parse("+OK"));
    }
}
