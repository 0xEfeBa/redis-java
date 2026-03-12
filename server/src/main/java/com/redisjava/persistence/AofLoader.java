package com.redisjava.persistence;

import com.redisjava.datastruct.Db;
import com.redisjava.protocol.RespParser;
import com.redisjava.protocol.RespToken;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * AOF file loader for startup recovery.
 * <p>
 * Reads and replays commands from AOF file to restore database state.
 * </p>
 */
public class AofLoader {

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB read buffer

    /**
     * Loads and replays commands from an AOF file.
     *
     * @param aofPath Path to the AOF file.
     * @param db      The database to restore.
     * @return Number of commands replayed.
     */
    public static int load(Path aofPath, Db db) {
        if (!Files.exists(aofPath)) {
            return 0;
        }

        int commandCount = 0;
        RespParser parser = new RespParser();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try (FileChannel channel = FileChannel.open(aofPath, StandardOpenOption.READ)) {
            while (channel.read(buffer) != -1 || buffer.position() > 0) {
                buffer.flip();

                RespToken token = parser.parse(buffer);
                while (token != null) {
                    if (replayCommand(token, db)) {
                        commandCount++;
                    }
                    token = parser.parse(buffer);
                }

                buffer.compact();
            }
        } catch (IOException e) {
            System.err.println("AOF load error: " + e.getMessage());
        }

        return commandCount;
    }

    /**
     * Replays a single command.
     *
     * @param command The parsed command.
     * @param db      The database.
     * @return true if command was replayed successfully.
     */
    private static boolean replayCommand(RespToken command, Db db) {
        if (command.getType() != RespToken.Type.ARRAY || command.getArrayLength() < 2) {
            return false;
        }

        RespToken[] args = command.getArrayElements();
        byte[] cmdName = args[0].getData();

        if (cmdName == null) {
            return false;
        }

        // Case-insensitive command matching
        if (equalsIgnoreCase(cmdName, "SET") && args.length >= 3) {
            byte[] key = args[1].getData();
            byte[] value = args[2].getData();
            if (key != null && value != null) {
                db.set(key, value);
                return true;
            }
        } else if (equalsIgnoreCase(cmdName, "DEL") && args.length >= 2) {
            for (int i = 1; i < args.length; i++) {
                byte[] key = args[i].getData();
                if (key != null) {
                    db.del(key);
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Case-insensitive byte array comparison.
     */
    private static boolean equalsIgnoreCase(byte[] data, String target) {
        byte[] targetBytes = target.getBytes();
        if (data.length != targetBytes.length) {
            return false;
        }
        for (int i = 0; i < data.length; i++) {
            byte b1 = toUpper(data[i]);
            byte b2 = toUpper(targetBytes[i]);
            if (b1 != b2) {
                return false;
            }
        }
        return true;
    }

    private static byte toUpper(byte b) {
        if (b >= 'a' && b <= 'z') {
            return (byte) (b - 32);
        }
        return b;
    }
}
