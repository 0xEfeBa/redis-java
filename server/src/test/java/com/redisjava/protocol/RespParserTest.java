package com.redisjava.protocol;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Unit tests for RespParser — state machine RESP ayrıştırıcısı.
 */
public class RespParserTest {

    private RespParser parser;
    @BeforeEach

    public void setup() {
        parser = new RespParser();
    }

    private ByteBuffer wrap(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(bytes.length);
        buf.put(bytes);
        buf.flip();
        return buf;
    }

    // ── Simple String ────────────────────────────────────────────────────
    @Test

    public void testParseSimpleString() {
        RespToken token = parser.parse(wrap("+OK\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.SIMPLE_STRING, token.getType());
        Assert.assertEquals("OK", new String(token.getData()));
    }
    @Test

    public void testParseSimpleStringWithSpaces() {
        RespToken token = parser.parse(wrap("+Hello World\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.SIMPLE_STRING, token.getType());
        Assert.assertEquals("Hello World", new String(token.getData()));
    }

    // ── Error ────────────────────────────────────────────────────────────
    @Test

    public void testParseError() {
        RespToken token = parser.parse(wrap("-ERR unknown command\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.ERROR, token.getType());
        Assert.assertEquals("ERR unknown command", new String(token.getData()));
    }

    // ── Integer ──────────────────────────────────────────────────────────
    @Test

    public void testParsePositiveInteger() {
        RespToken token = parser.parse(wrap(":1000\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.INTEGER, token.getType());
        Assert.assertEquals(1000L, token.getIntegerValue());
    }
    @Test

    public void testParseNegativeInteger() {
        RespToken token = parser.parse(wrap(":-456\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.INTEGER, token.getType());
        Assert.assertEquals(-456L, token.getIntegerValue());
    }
    @Test

    public void testParseZeroInteger() {
        RespToken token = parser.parse(wrap(":0\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.INTEGER, token.getType());
        Assert.assertEquals(0L, token.getIntegerValue());
    }

    // ── Bulk String ──────────────────────────────────────────────────────
    @Test

    public void testParseBulkString() {
        RespToken token = parser.parse(wrap("$5\r\nhello\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.BULK_STRING, token.getType());
        Assert.assertEquals("hello", new String(token.getData()));
    }
    @Test

    public void testParseNullBulkString() {
        RespToken token = parser.parse(wrap("$-1\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.NULL, token.getType());
        Assert.assertTrue("isNull", token.isNull());
    }
    @Test

    public void testParseEmptyBulkString() {
        RespToken token = parser.parse(wrap("$0\r\n\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.BULK_STRING, token.getType());
        Assert.assertEquals(0, token.getData().length);
    }
    @Test

    public void testParseBulkStringBinaryData() {
        // $3\r\n + three bytes 0,1,2 + \r\n
        byte[] raw = new byte[]{'$','3','\r','\n', 0, 1, 2, '\r','\n'};
        ByteBuffer buf = ByteBuffer.wrap(raw);
        RespToken token = parser.parse(buf);
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.BULK_STRING, token.getType());
        Assert.assertEquals(3, token.getData().length);
        Assert.assertEquals(0, token.getData()[0]);
        Assert.assertEquals(1, token.getData()[1]);
        Assert.assertEquals(2, token.getData()[2]);
    }

    // ── Array ────────────────────────────────────────────────────────────
    @Test

    public void testParseEmptyArray() {
        RespToken token = parser.parse(wrap("*0\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.ARRAY, token.getType());
        Assert.assertEquals(0, token.getArrayLength());
    }
    @Test

    public void testParseNullArray() {
        RespToken token = parser.parse(wrap("*-1\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.NULL, token.getType());
        Assert.assertTrue("isNull", token.isNull());
    }
    @Test

    public void testParseArrayWithBulkStrings() {
        RespToken token = parser.parse(wrap("*2\r\n$3\r\nGET\r\n$5\r\nmykey\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.ARRAY, token.getType());
        Assert.assertEquals(2, token.getArrayLength());
        RespToken[] elems = token.getArrayElements();
        Assert.assertEquals("GET", new String(elems[0].getData()));
        Assert.assertEquals("mykey", new String(elems[1].getData()));
    }
    @Test

    public void testParseSetCommand() {
        RespToken token = parser.parse(wrap("*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.ARRAY, token.getType());
        Assert.assertEquals(3, token.getArrayLength());
        RespToken[] elems = token.getArrayElements();
        Assert.assertEquals("SET",     new String(elems[0].getData()));
        Assert.assertEquals("mykey",   new String(elems[1].getData()));
        Assert.assertEquals("myvalue", new String(elems[2].getData()));
    }
    @Test

    public void testParseArrayMixedTypes() {
        // *3 : integer, simple string, null bulk
        RespToken token = parser.parse(wrap("*3\r\n:100\r\n+OK\r\n$-1\r\n"));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.ARRAY, token.getType());
        Assert.assertEquals(3, token.getArrayLength());
        RespToken[] e = token.getArrayElements();
        Assert.assertEquals(RespToken.Type.INTEGER, e[0].getType());
        Assert.assertEquals(100L, e[0].getIntegerValue());
        Assert.assertEquals(RespToken.Type.SIMPLE_STRING, e[1].getType());
        Assert.assertEquals("OK", new String(e[1].getData()));
        Assert.assertEquals(RespToken.Type.NULL, e[2].getType());
    }

    // ── Partial Read ─────────────────────────────────────────────────────
    @Test

    public void testPartialRead_returnsNull() {
        // Sadece ilk yarı — tamamlanmamış veri
        RespToken token = parser.parse(wrap("$5\r\nhel"));
        Assert.assertNull(token);
    }

    // ── Dynamic buffer resize ─────────────────────────────────────────────
    @Test

    public void testDynamicBufferResize_longSimpleString() {
        // 1000 karakterlik simple string (varsayılan buffer 256, birden fazla resize)
        StringBuilder sb = new StringBuilder("+");
        for (int i = 0; i < 1000; i++) sb.append('A');
        sb.append("\r\n");
        RespToken token = parser.parse(wrap(sb.toString()));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.SIMPLE_STRING, token.getType());
        Assert.assertEquals(1000, token.getData().length);
    }
    @Test

    public void testLongArrayLengthLine() {
        // *000...001\r\n  (uzun sayı satırı)
        StringBuilder sb = new StringBuilder("*");
        for (int i = 0; i < 300; i++) sb.append('0');
        sb.append("1\r\n$1\r\na\r\n");
        RespToken token = parser.parse(wrap(sb.toString()));
        Assert.assertNotNull(token);
        Assert.assertEquals(RespToken.Type.ARRAY, token.getType());
        Assert.assertEquals(1, token.getArrayLength());
        Assert.assertEquals("a", new String(token.getArrayElements()[0].getData()));
    }

    // ── SafeEncoder ──────────────────────────────────────────────────────
    @Test

    public void testBytesToLong_positive() {
        byte[] data = "12345".getBytes();
        Assert.assertEquals(12345L, SafeEncoder.bytesToLong(data, 0, data.length));
    }
    @Test

    public void testBytesToLong_negative() {
        byte[] data = "-9876".getBytes();
        Assert.assertEquals(-9876L, SafeEncoder.bytesToLong(data, 0, data.length));
    }
    @Test

    public void testEqualsIgnoreCase() {
        byte[] lower = "get".getBytes();
        byte[] upper = "GET".getBytes();
        Assert.assertTrue("get == GET", SafeEncoder.equalsIgnoreCase(lower, 0, lower.length, upper));
    }
}
