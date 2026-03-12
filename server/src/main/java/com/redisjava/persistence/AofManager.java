package com.redisjava.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import com.redisjava.protocol.RespToken;

/**
 * Append-Only File (AOF) persistence manager.
 * <p>
 * Logs all write commands to disk in RESP format.
 * On startup, replays the AOF to restore state.
 * </p>
 */
public class AofManager {

    private static final int BUFFER_SIZE = 16 * 1024; // 16KB buffer

    private static AofManager instance;

    private final Path aofPath;
    private final ByteBuffer buffer;
    private FileChannel channel;
    private boolean enabled;

    /**
     * Initializes the AOF manager.
     *
     * @param aofPath Path to the AOF file.
     */
    public static void init(Path aofPath) {
        instance = new AofManager(aofPath);
    }

    /**
     * Gets the singleton instance.
     *
     * @return The AOF manager, or null if not initialized.
     */
    public static AofManager getInstance() {
        return instance;
    }

    private AofManager(Path aofPath) {
        this.aofPath = aofPath;
        this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.enabled = false;
    }

    /**
     * Opens the AOF file for writing.
     *
     * @throws IOException If the file cannot be opened.
     */
    public void open() throws IOException {
        this.channel = FileChannel.open(aofPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        this.enabled = true;
    }

    /**
     * Appends a SET command to the AOF.
     *
     * @param key   The key bytes.
     * @param value The value bytes.
     */
    /**
     * Appends a generic command to the AOF.
     *
     * @param args The command arguments as RespTokens.
     */
    public void append(RespToken[] args) {
        if (!enabled)
            return;

        appendArrayHeader(args.length);
        for (RespToken arg : args) {
            // We assume args are BulkStrings (standard for commands)
            if (arg.getType() == RespToken.Type.BULK_STRING) {
                appendBulkString(arg.getData());
            } else {
                // Should not happen for standard commands parsed by generic parser
                // But handle gracefully? Or just ignore/toString?
                // For now assume bulk string as per RESP command format
            }
        }
        flushIfNeeded();
    }

    /**
     * Appends a SET command to the AOF.
     * 
     * @deprecated Use append(RespToken[] args) instead.
     */
    public void appendSet(byte[] key, byte[] value) {
        if (!enabled)
            return;

        // *3\r\n$3\r\nSET\r\n$keylen\r\nkey\r\n$valuelen\r\nvalue\r\n
        appendArrayHeader(3);
        appendBulkString("SET".getBytes());
        appendBulkString(key);
        appendBulkString(value);
        flushIfNeeded();
    }

    /**
     * Appends a DEL command to the AOF.
     * 
     * @deprecated Use append(RespToken[] args) instead.
     */
    public void appendDel(byte[] key) {
        if (!enabled)
            return;

        // *2\r\n$3\r\nDEL\r\n$keylen\r\nkey\r\n
        appendArrayHeader(2);
        appendBulkString("DEL".getBytes());
        appendBulkString(key);
        flushIfNeeded();
    }

    /**
     * Writes RESP array header to buffer.
     */
    private void appendArrayHeader(int count) {
        ensureCapacity(16); // Max: *999999\r\n
        buffer.put((byte) '*');
        putNumber(count);
        buffer.put((byte) '\r');
        buffer.put((byte) '\n');
    }

    /**
     * Writes RESP bulk string to buffer.
     */
    private void appendBulkString(byte[] data) {
        // Header: $length\r\n (max ~12 bytes)
        int headerSize = 1 + numDigits(data.length) + 2;
        ensureCapacity(headerSize);
        buffer.put((byte) '$');
        putNumber(data.length);
        buffer.put((byte) '\r');
        buffer.put((byte) '\n');

        // Data + trailing CRLF
        if (data.length + 2 <= buffer.remaining()) {
            // Fits in buffer
            buffer.put(data);
            buffer.put((byte) '\r');
            buffer.put((byte) '\n');
        } else {
            // Too large for buffer - flush and write directly
            flush();
            try {
                writeFully(ByteBuffer.wrap(data));
                writeFully(ByteBuffer.wrap(new byte[] { '\r', '\n' }));
            } catch (IOException e) {
                System.err.println("AOF write error: " + e.getMessage());
            }
        }
    }

    /**
     * Ensures buffer has at least 'needed' bytes of capacity.
     */
    private void ensureCapacity(int needed) {
        if (buffer.remaining() < needed) {
            flush();
        }
    }

    private void writeFully(ByteBuffer data) throws IOException {
        while (data.hasRemaining()) {
            channel.write(data);
        }
    }

    /**
     * Returns number of digits in a number.
     */
    private int numDigits(int num) {
        if (num == 0)
            return 1;
        return (int) Math.log10(num) + 1;
    }

    /**
     * Writes integer to buffer as ASCII.
     */
    private void putNumber(int num) {
        byte[] bytes = String.valueOf(num).getBytes();
        buffer.put(bytes);
    }

    /**
     * Flushes buffer to file if it's getting full.
     */
    private void flushIfNeeded() {
        if (buffer.position() > BUFFER_SIZE - 1024) {
            flush();
        }
    }

    /**
     * Forces all buffered data to disk.
     */
    public void flush() {
        if (!enabled || buffer.position() == 0)
            return;

        try {
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();
        } catch (IOException e) {
            System.err.println("AOF flush error: " + e.getMessage());
        }
    }

    /**
     * Syncs data to disk (fsync).
     */
    public void sync() {
        if (!enabled)
            return;

        flush();
        try {
            channel.force(true);
        } catch (IOException e) {
            System.err.println("AOF sync error: " + e.getMessage());
        }
    }

    /**
     * Closes the AOF file.
     */
    public void close() {
        if (channel == null)
            return;

        flush();
        try {
            channel.close();
        } catch (IOException e) {
            // Ignore close errors
        }
        enabled = false;
    }

    /**
     * @return true if AOF is enabled and open.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The AOF file path.
     */
    public Path getPath() {
        return aofPath;
    }

    /**
     * Serialises command arguments into a RESP array byte array.
     *
     * @param args Command tokens (must be BULK_STRING).
     * @return RESP-formatted bytes ready to write verbatim to the AOF.
     */
    public static byte[] serializeCommand(RespToken[] args) {
        if (args == null || args.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(args.length).append("\r\n");
        for (RespToken arg : args) {
            if (arg.getType() == RespToken.Type.BULK_STRING) {
                byte[] data = arg.getData();
                sb.append('$').append(data.length).append("\r\n");
                sb.append(new String(data)).append("\r\n");
            }
        }
        return sb.toString().getBytes();
    }

    /**
     * Serialises and enqueues a command for async AOF writing via {@link AofQueue}.
     * Returns immediately without blocking on I/O.
     *
     * @param args Command tokens.
     */
    public void appendAsync(RespToken[] args) {
        if (!enabled) return;
        byte[] entry = serializeCommand(args);
        if (entry != null) {
            AofQueue.getInstance().offer(entry);
        }
    }

}