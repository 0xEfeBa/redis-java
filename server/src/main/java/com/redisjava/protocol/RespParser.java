package com.redisjava.protocol;

import java.nio.ByteBuffer;

/**
 * State machine-based RESP (Redis Serialization Protocol) parser.
 * <p>
 * Handles incremental parsing of RESP data from network buffers.
 * Can resume parsing when more data arrives (handles partial reads).
 * </p>
 * <p>
 * Thread-safety: NOT thread-safe. Each connection should have its own parser.
 * </p>
 */
public class RespParser {

    // RESP type markers
    private static final byte SIMPLE_STRING = '+';
    private static final byte ERROR = '-';
    private static final byte INTEGER = ':';
    private static final byte BULK_STRING = '$';
    private static final byte ARRAY = '*';
    private static final byte CR = '\r';
    private static final byte LF = '\n';

    // Security: Maximum allowed bulk string size (64MB) to prevent OOM attacks
    private static final int MAX_BULK_SIZE = 64 * 1024 * 1024;

    // Parser states
    private static final int STATE_READ_TYPE = 0;
    private static final int STATE_READ_LENGTH = 1;
    private static final int STATE_READ_BULK_DATA = 2;
    private static final int STATE_READ_CRLF = 3;
    private static final int STATE_READ_INLINE = 4;

    // Parser state
    private int state = STATE_READ_TYPE;
    private byte currentType;
    private int expectedLength;
    private byte[] bulkData;
    private int bulkDataIndex;

    // For array parsing
    private RespToken[] arrayElements;
    private int arrayIndex;
    private int arraySize;

    // Temporary buffer for line reading
    private byte[] lineBuffer = new byte[256];
    private int lineIndex = 0;

    /**
     * Parses RESP data from the given buffer.
     * <p>
     * Buffer should be in read mode (flipped). After parsing,
     * any remaining data stays in the buffer.
     * </p>
     *
     * @param buffer The buffer containing RESP data.
     * @return Parsed RespToken, or null if more data needed.
     */
    public RespToken parse(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            RespToken result = parseStep(buffer);
            if (result != null) {
                reset();
                return result;
            }
        }
        return null;
    }

    /**
     * Single step of parsing.
     */
    private RespToken parseStep(ByteBuffer buffer) {
        switch (state) {
            case STATE_READ_TYPE:
                return readType(buffer);
            case STATE_READ_LENGTH:
                return readLength(buffer);
            case STATE_READ_BULK_DATA:
                return readBulkData(buffer);
            case STATE_READ_CRLF:
                return readCrlf(buffer);
            case STATE_READ_INLINE:
                return readInline(buffer);
            default:
                throw new IllegalStateException("Unknown state: " + state);
        }
    }

    /**
     * Reads the type marker byte.
     */
    private RespToken readType(ByteBuffer buffer) {
        currentType = buffer.get();
        lineIndex = 0;

        switch (currentType) {
            case SIMPLE_STRING:
            case ERROR:
            case INTEGER:
                state = STATE_READ_LENGTH;
                return null;
            case BULK_STRING:
            case ARRAY:
                state = STATE_READ_LENGTH;
                return null;

            default:
                // Fallback to inline command parsing
                // Rewind buffer by 1 to include the first character in the command
                buffer.position(buffer.position() - 1);
                state = STATE_READ_INLINE;
                return null;
        }
    }

    /**
     * Reads until CRLF to get length or simple value.
     */
    private RespToken readLength(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();

            if (b == CR) {
                continue;
            }

            if (b == LF) {
                return processLine();
            }

            if (lineIndex >= lineBuffer.length) {
                ensureCapacity();
            }
            lineBuffer[lineIndex++] = b;
        }
        return null;
    }

    private void ensureCapacity() {
        byte[] newBuffer = new byte[lineBuffer.length * 2];
        System.arraycopy(lineBuffer, 0, newBuffer, 0, lineBuffer.length);
        lineBuffer = newBuffer;
    }

    /**
     * Processes a complete line based on current type.
     */
    private RespToken processLine() {
        RespToken token;
        switch (currentType) {
            case SIMPLE_STRING:
                token = createSimpleString();
                break;
            case ERROR:
                token = createError();
                break;
            case INTEGER:
                token = createInteger();
                break;
            case BULK_STRING:
                token = startBulkString();
                if (token != null && arrayElements != null) {
                    return addArrayElement(token);
                }
                return token; // Bulk string needs more processing or is complete token
            case ARRAY:
                return startArray(); // Array needs more processing
            default:
                throw new IllegalStateException("Unexpected type: " + (char) currentType);
        }

        // If we're inside an array, add this token as an element
        if (arrayElements != null) {
            return addArrayElement(token);
        }

        return token;
    }

    /**
     * Creates simple string token from line buffer.
     */
    private RespToken createSimpleString() {
        byte[] data = new byte[lineIndex];
        System.arraycopy(lineBuffer, 0, data, 0, lineIndex);
        return RespToken.simpleString(data);
    }

    /**
     * Creates error token from line buffer.
     */
    private RespToken createError() {
        byte[] data = new byte[lineIndex];
        System.arraycopy(lineBuffer, 0, data, 0, lineIndex);
        return RespToken.error(data);
    }

    /**
     * Creates integer token from line buffer.
     */
    private RespToken createInteger() {
        long value = SafeEncoder.bytesToLong(lineBuffer, 0, lineIndex);
        return RespToken.integer(value);
    }

    /**
     * Starts bulk string parsing.
     */
    private RespToken startBulkString() {
        expectedLength = SafeEncoder.bytesToInt(lineBuffer, 0, lineIndex);

        if (expectedLength == -1) {
            return RespToken.nullBulkString();
        }

        if (expectedLength == 0) {
            bulkData = new byte[0];
            state = STATE_READ_CRLF;
            return null;
        }

        // Security check
        if (expectedLength > MAX_BULK_SIZE) {
            throw new IllegalStateException(
                    "Protocol error: Bulk string too large (" + expectedLength + " bytes). Max: " + MAX_BULK_SIZE);
        }

        bulkData = new byte[expectedLength];
        bulkDataIndex = 0;
        state = STATE_READ_BULK_DATA;
        return null;
    }

    /**
     * Reads bulk string data.
     */
    private RespToken readBulkData(ByteBuffer buffer) {
        int remaining = expectedLength - bulkDataIndex;
        int available = buffer.remaining();
        int toRead = Math.min(remaining, available);

        buffer.get(bulkData, bulkDataIndex, toRead);
        bulkDataIndex += toRead;

        if (bulkDataIndex >= expectedLength) {
            state = STATE_READ_CRLF;
        }
        return null;
    }

    /**
     * Reads trailing CRLF after bulk data.
     */
    private RespToken readCrlf(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == LF) {
                if (arrayElements != null) {
                    return addArrayElement(RespToken.bulkString(bulkData));
                }
                return RespToken.bulkString(bulkData);
            }
        }
        return null;
    }

    /**
     * Starts array parsing.
     */
    private RespToken startArray() {
        arraySize = SafeEncoder.bytesToInt(lineBuffer, 0, lineIndex);

        if (arraySize == -1) {
            return RespToken.nullArray();
        }

        if (arraySize == 0) {
            return RespToken.array(new RespToken[0]);
        }

        arrayElements = new RespToken[arraySize];
        arrayIndex = 0;
        state = STATE_READ_TYPE;
        return null;
    }

    /**
     * Adds element to current array being parsed.
     */
    private RespToken addArrayElement(RespToken element) {
        arrayElements[arrayIndex++] = element;

        if (arrayIndex >= arraySize) {
            RespToken[] result = arrayElements;
            arrayElements = null;
            return RespToken.array(result);
        }

        state = STATE_READ_TYPE;
        return null;
    }

    /**
     * Reads inline command (space separated).
     */
    private RespToken readInline(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();

            if (b == CR) {
                continue;
            }

            if (b == LF) {
                return processInlineCommand();
            }

            if (lineIndex >= lineBuffer.length) {
                ensureCapacity();
            }
            lineBuffer[lineIndex++] = b;
        }
        return null;
    }

    private RespToken processInlineCommand() {
        if (lineIndex == 0) {
            return null; // Empty line
        }

        // Convert line to string and split by whitespace
        String line = new String(lineBuffer, 0, lineIndex);
        String[] parts = line.trim().split("\\s+");

        if (parts.length == 0) {
            return null;
        }

        RespToken[] elements = new RespToken[parts.length];
        for (int i = 0; i < parts.length; i++) {
            elements[i] = RespToken.bulkString(parts[i].getBytes());
        }

        return RespToken.array(elements);
    }

    /**
     * Resets parser state for next message.
     */
    public void reset() {
        state = STATE_READ_TYPE;
        currentType = 0;
        expectedLength = 0;
        bulkData = null;
        bulkDataIndex = 0;
        arrayElements = null;
        arrayIndex = 0;
        arraySize = 0;
        lineIndex = 0;
    }
}
