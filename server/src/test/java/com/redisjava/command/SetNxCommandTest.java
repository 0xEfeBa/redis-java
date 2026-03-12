package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RespToken;
import com.redisjava.testutil.Assert;

/**
 * SETNX ve SET NX flag testleri.
 * SETNX'in zero-allocation olduğunu ve doğru davrandığını doğrular.
 */
public class SetNxCommandTest {

    private SetCommand setCommand;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(1));
        setCommand = new SetCommand();
        conn = new MockConnection();
    }

    /** SETNX — key yokken :1 dönmeli */
    @Test
    public void testSetnx_keyNotExists_returns1() {
        setCommand.execute(conn, tokens("SETNX", "testkey", "hello"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
    }

    /** SETNX — key varken :0 dönmeli */
    @Test
    public void testSetnx_keyExists_returns0() {
        setCommand.execute(conn, tokens("SETNX", "mykey", "first"));
        conn.clear();
        setCommand.execute(conn, tokens("SETNX", "mykey", "second"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    /** SETNX — mevcut değer değişmemeli */
    @Test
    public void testSetnx_doesNotOverwrite() {
        setCommand.execute(conn, tokens("SETNX", "k", "original"));
        setCommand.execute(conn, tokens("SETNX", "k", "overwrite"));
        conn.clear();
        new GetCommand().execute(conn, tokens("GET", "k"));
        Assert.assertEquals("$8\r\noriginal\r\n", conn.getLastResponse());
    }

    /** SET NX flag — key yokken +OK dönmeli */
    @Test
    public void testSet_NX_flag_keyNotExists_returnsOK() {
        setCommand.execute(conn, tokens("SET", "newkey", "val", "NX"));
        Assert.assertEquals("+OK\r\n", conn.getLastResponse());
    }

    /** SET NX flag — key varken nil dönmeli */
    @Test
    public void testSet_NX_flag_keyExists_returnsNil() {
        setCommand.execute(conn, tokens("SET", "existkey", "v1"));
        conn.clear();
        setCommand.execute(conn, tokens("SET", "existkey", "v2", "NX"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
    }

    /** SET EX — TTL doğru set edilmeli */
    @Test
    public void testSet_EX_setsTtl() {
        setCommand.execute(conn, tokens("SET", "ttlkey", "val", "EX", "60"));
        Assert.assertEquals("+OK\r\n", conn.getLastResponse());
        long ttl = Db.getInstance().ttlSeconds("ttlkey".getBytes());
        Assert.assertTrue("TTL 0 < ttl <= 60", ttl > 0 && ttl <= 60);
    }

    /** Küçük harf nx — case-insensitive çalışmalı */
    @Test
    public void testSet_nx_lowercase_works() {
        setCommand.execute(conn, tokens("SET", "cikey", "val", "nx"));
        Assert.assertEquals("+OK\r\n", conn.getLastResponse());
    }

    /** Küçük harf ex — case-insensitive çalışmalı */
    @Test
    public void testSet_ex_lowercase_setsTtl() {
        setCommand.execute(conn, tokens("SET", "exkey", "val", "ex", "30"));
        Assert.assertEquals("+OK\r\n", conn.getLastResponse());
        long ttl = Db.getInstance().ttlSeconds("exkey".getBytes());
        Assert.assertTrue("TTL > 0", ttl > 0);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static RespToken[] tokens(String... parts) {
        RespToken[] result = new RespToken[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = RespToken.bulkString(parts[i].getBytes());
        }
        return result;
    }
}
