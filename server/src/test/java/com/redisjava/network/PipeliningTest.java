package com.redisjava.network;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.command.MockConnection;
import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RedisProtocolHandler;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

/**
 * RESP Pipelining testleri.
 * Tek buffer'da birden fazla komutun doğru işlendiğini doğrular.
 */
public class PipeliningTest {

    private RedisProtocolHandler handler;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(2));
        handler = new RedisProtocolHandler();
        conn = new MockConnection();
    }

    /** Tek komut pipeline — temel çalışma */
    @Test
    public void testSingleCommand_works() {
        byte[] ping = "*1\r\n$4\r\nPING\r\n".getBytes();
        ByteBuffer buf = ByteBuffer.wrap(ping);
        handler.handle(conn, buf);
        assertEquals("+PONG\r\n", conn.getLastResponse());
    }

    /** 3 PING pipeline — tümü yanıt almalı */
    @Test
    public void testPipeline_threePings() {
        String pipeline = "*1\r\n$4\r\nPING\r\n" +
                          "*1\r\n$4\r\nPING\r\n" +
                          "*1\r\n$4\r\nPING\r\n";
        ByteBuffer buf = ByteBuffer.wrap(pipeline.getBytes());
        handler.handle(conn, buf);

        String resp = conn.getLastResponse();
        assertNotNull(resp);
        // 3 PONG yanıtı olmalı
        int count = 0;
        int idx = 0;
        while ((idx = resp.indexOf("+PONG\r\n", idx)) != -1) {
            count++;
            idx += "+PONG\r\n".length();
        }
        assertEquals(3, count);
    }

    /** SET + GET pipeline — sıralı yanıtlar doğru */
    @Test
    public void testPipeline_setThenGet() {
        String pipeline =
            "*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n" +
            "*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n";
        ByteBuffer buf = ByteBuffer.wrap(pipeline.getBytes());
        handler.handle(conn, buf);

        String resp = conn.getLastResponse();
        assertNotNull(resp);
        assertTrue(resp.contains("+OK\r\n"), "SET sonucu +OK içermeli");
        assertTrue(resp.contains("$3\r\nbar\r\n"), "GET sonucu bar içermeli");
    }

    /** 10 SET pipeline — tümü OK */
    @Test
    public void testPipeline_10Sets_allOK() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("*3\r\n$3\r\nSET\r\n$4\r\nkey").append(i)
              .append("\r\n$3\r\nval\r\n");
        }
        ByteBuffer buf = ByteBuffer.wrap(sb.toString().getBytes());
        handler.handle(conn, buf);

        String resp = conn.getLastResponse();
        int okCount = 0;
        int idx = 0;
        while ((idx = resp.indexOf("+OK\r\n", idx)) != -1) {
            okCount++;
            idx += "+OK\r\n".length();
        }
        assertEquals(10, okCount);
    }

    /** Yarım komut (partial buffer) — sonraki buffer'da tamamlanmalı */
    @Test
    public void testPipeline_partialThenComplete() {
        // Sadece komutun ilk yarısı
        byte[] part1 = "*1\r\n$4\r\nPI".getBytes();
        byte[] part2 = "NG\r\n".getBytes();

        ByteBuffer buf1 = ByteBuffer.wrap(part1);
        handler.handle(conn, buf1);
        // Henüz yanıt gelmemeli
        String resp1 = conn.getLastResponse();
        assertTrue(resp1 == null || resp1.isEmpty(), "Partial — yanıt yok veya boş");

        conn.clear();
        ByteBuffer buf2 = ByteBuffer.wrap(part2);
        handler.handle(conn, buf2);
        assertEquals("+PONG\r\n", conn.getLastResponse());
    }

    /** Hatalı komut pipeline içinde — hata sonraki komutları etkilememeli */
    @Test
    public void testPipeline_errorDoesNotStopPipeline() {
        String pipeline =
            "*1\r\n$4\r\nPING\r\n" +
            "*1\r\n$7\r\nUNKNOWN\r\n" +
            "*1\r\n$4\r\nPING\r\n";
        ByteBuffer buf = ByteBuffer.wrap(pipeline.getBytes());
        handler.handle(conn, buf);

        String resp = conn.getLastResponse();
        assertTrue(resp.contains("+PONG\r\n"), "PONG içermeli");
        assertTrue(resp.contains("-ERR"), "ERR içermeli");
    }
}
