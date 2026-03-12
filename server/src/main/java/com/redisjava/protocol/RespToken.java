package com.redisjava.protocol;

/**
 * Represents a parsed RESP (Redis Serialization Protocol) element.
 * <p>
 * RESP supports 5 data types:
 * <ul>
 * <li>Simple String: +OK\r\n</li>
 * <li>Error: -ERR message\r\n</li>
 * <li>Integer: :1000\r\n</li>
 * <li>Bulk String: $5\r\nhello\r\n</li>
 * <li>Array: *2\r\n...</li>
 * </ul>
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class RespToken {

    /**
     * RESP data types.
     */
    public enum Type {
        SIMPLE_STRING,
        ERROR,
        INTEGER,
        BULK_STRING,
        ARRAY,
        NULL
    }

    private final Type type;
    private final byte[] data;
    private final long integerValue;
    private final RespToken[] arrayElements;

    /**
     * Private constructor - use static factory methods.
     */
    private RespToken(Type type, byte[] data, long integerValue, RespToken[] arrayElements) {
        this.type = type;
        this.data = data;
        this.integerValue = integerValue;
        this.arrayElements = arrayElements;
    }

    // Optimization: Flyweight pattern for commonly used tokens
    private static final RespToken NULL_BULK_STRING_TOKEN = new RespToken(Type.NULL, null, 0, null);
    private static final RespToken NULL_ARRAY_TOKEN = new RespToken(Type.NULL, null, 0, null);

    // ========== Static Factory Methods ==========

    /**
     * Creates a Simple String token.
     *
     * @param data The string data as bytes.
     * @return New RespToken.
     */
    public static RespToken simpleString(byte[] data) {
        return new RespToken(Type.SIMPLE_STRING, data, 0, null);
    }

    /**
     * Creates an Error token.
     *
     * @param data The error message as bytes.
     * @return New RespToken.
     */
    public static RespToken error(byte[] data) {
        return new RespToken(Type.ERROR, data, 0, null);
    }

    /**
     * Creates an Integer token.
     *
     * @param value The integer value.
     * @return New RespToken.
     */
    public static RespToken integer(long value) {
        return new RespToken(Type.INTEGER, null, value, null);
    }

    /**
     * Creates a Bulk String token.
     *
     * @param data The string data as bytes.
     * @return New RespToken.
     */
    public static RespToken bulkString(byte[] data) {
        return new RespToken(Type.BULK_STRING, data, 0, null);
    }

    /**
     * Creates an Array token.
     *
     * @param elements The array elements.
     * @return New RespToken.
     */
    public static RespToken array(RespToken[] elements) {
        return new RespToken(Type.ARRAY, null, 0, elements);
    }

    /**
     * Creates a Null Bulk String token.
     *
     * @return New RespToken representing null.
     */
    public static RespToken nullBulkString() {
        return NULL_BULK_STRING_TOKEN;
    }

    /**
     * Creates a Null Array token.
     * Used when parsing *-1 (null array).
     *
     * @return New RespToken representing null array.
     */
    public static RespToken nullArray() {
        return NULL_ARRAY_TOKEN;
    }

    // ========== Getters ==========

    /**
     * @return The token type.
     */
    public Type getType() {
        return type;
    }

    /**
     * @return The raw byte data (for STRING types).
     */
    public byte[] getData() {
        return data;
    }

    /**
     * @return The integer value (for INTEGER type).
     */
    public long getIntegerValue() {
        return integerValue;
    }

    /**
     * @return The array elements (for ARRAY type).
     */
    public RespToken[] getArrayElements() {
        return arrayElements;
    }

    /**
     * @return Number of array elements, or 0 if not an array.
     */
    public int getArrayLength() {
        return arrayElements != null ? arrayElements.length : 0;
    }

    /**
     * @return true if this is a null bulk string.
     */
    public boolean isNull() {
        return type == Type.NULL;
    }

    /**
     * Returns the data as a byte array if this is a Bulk String or Simple String.
     * 
     * @return The data bytes.
     * @throws IllegalStateException if not a string type.
     */
    public byte[] getBulkString() {
        if (type == Type.BULK_STRING || type == Type.SIMPLE_STRING) {
            return data;
        }
        // Improve tolerance: if INTEGER, convert to string bytes?
        // standard Redis checks strict types often but "getBulkString" usually implies
        // "give me the bytes of the argument".
        // Arguments from parser are usually BulkStrings.
        if (type == Type.INTEGER) {
            return String.valueOf(integerValue).getBytes();
        }
        throw new IllegalStateException("Not a string type: " + type);
    }
}
