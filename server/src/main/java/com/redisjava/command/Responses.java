package com.redisjava.command;

/**
 * Pre-allocated RESP response byte arrays.
 * <p>
 * These static responses avoid allocation in hot paths.
 * All responses are complete RESP-formatted messages ready to send.
 * </p>
 */
public final class Responses {

    private Responses() {
    }

    // ===== Simple String Responses =====

    /** +PONG\r\n - Response to PING command */
    public static final byte[] PONG = "+PONG\r\n".getBytes();

    /** +OK\r\n - Generic success response */
    public static final byte[] OK = "+OK\r\n".getBytes();

    /** +QUEUED\r\n - Transaction queue response */
    public static final byte[] QUEUED = "+QUEUED\r\n".getBytes();

    // ===== Error Responses =====

    /** -ERR unknown command\r\n */
    public static final byte[] ERR_UNKNOWN_COMMAND = "-ERR unknown command\r\n".getBytes();

    /** -ERR wrong number of arguments\r\n */
    public static final byte[] ERR_WRONG_ARGS = "-ERR wrong number of arguments\r\n".getBytes();

    /** -ERR syntax error\r\n */
    public static final byte[] ERR_SYNTAX = "-ERR syntax error\r\n".getBytes();

    /** -OOM command not allowed when used memory > 'maxmemory'\r\n */
    public static final byte[] ERR_OOM = "-OOM command not allowed when used memory > 'maxmemory'\r\n".getBytes();

    /** -WRONGTYPE Operation against a key holding the wrong kind of value\r\n */
    public static final byte[] ERR_WRONGTYPE = "-WRONGTYPE Operation against a key holding the wrong kind of value\r\n".getBytes();

    /** Alias: WRONG_TYPE_ERROR → ERR_WRONGTYPE */
    public static final byte[] WRONG_TYPE_ERROR = ERR_WRONGTYPE;

    /** Alias: OOM_ERROR → ERR_OOM */
    public static final byte[] OOM_ERROR = ERR_OOM;

    /** -ERR value is not an integer or out of range\r\n */
    public static final byte[] ERR_NOT_INTEGER = "-ERR value is not an integer or out of range\r\n".getBytes();

    // ===== Null Response =====

    /** $-1\r\n - Null bulk string (key not found) */
    public static final byte[] NULL_BULK_STRING = "$-1\r\n".getBytes();

    /** Alias for NULL_BULK_STRING */
    public static final byte[] NULL_BULK = NULL_BULK_STRING;

    /** *0\r\n - Empty RESP array */
    public static final byte[] EMPTY_ARRAY = "*0\r\n".getBytes();

    // ===== Integer Responses =====

    /** :0\r\n */
    public static final byte[] ZERO = ":0\r\n".getBytes();

    /** :1\r\n */
    public static final byte[] ONE = ":1\r\n".getBytes();

    // ===== Common Prefixes =====

    /** Bulk string prefix: $ */
    public static final byte BULK_PREFIX = '$';

    /** CRLF: \r\n */
    public static final byte[] CRLF = "\r\n".getBytes();
}
