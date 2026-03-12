package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.stats.ServerStats;
import com.redisjava.datastruct.Db;

/**
 * INFO command implementation.
 * <p>
 * Returns server information including memory and connection statistics.
 * This is not a hot-path command, so String usage is acceptable.
 * </p>
 */
public class InfoCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        String info = buildInfoString();
        writeBulkString(connection, info.getBytes());
    }

    private String buildInfoString() {
        ServerStats stats = ServerStats.getInstance();
        StringBuilder sb = new StringBuilder();

        // Server section
        sb.append("# Server\r\n");
        sb.append("redis_version:redis-java-1.0\r\n");
        sb.append("redis_mode:standalone\r\n");
        sb.append("\r\n");

        // Memory section
        sb.append("# Memory\r\n");
        try {
            long usedMemory = Db.getInstance().getUsedMemory();
            sb.append("used_memory:").append(usedMemory).append("\r\n");
            sb.append("used_memory_human:").append(formatHumanReadable(usedMemory)).append("\r\n");
        } catch (Exception e) {
            sb.append("used_memory:0\r\n"); // Fallback
            sb.append("used_memory_human:0B\r\n");
        }
        sb.append("\r\n");

        // Stats section
        sb.append("# Stats\r\n");
        sb.append("total_connections_received:").append(stats.getTotalConnections()).append("\r\n");
        sb.append("total_commands_processed:").append(stats.getTotalCommands()).append("\r\n");
        sb.append("connected_clients:").append(stats.getCurrentConnections()).append("\r\n");
        sb.append("keyspace_hits:").append(stats.getKeyspaceHits()).append("\r\n");
        sb.append("keyspace_misses:").append(stats.getKeyspaceMisses()).append("\r\n");
        sb.append("expired_keys:").append(stats.getExpiredKeys()).append("\r\n");
        sb.append("evicted_keys:").append(stats.getEvictedKeys()).append("\r\n");

        return sb.toString();
    }

    private void writeBulkString(Connection connection, byte[] data) {
        byte[] lengthBytes = String.valueOf(data.length).getBytes();

        int totalSize = 1 + lengthBytes.length + 2 + data.length + 2;
        byte[] response = new byte[totalSize];

        int pos = 0;
        response[pos++] = Responses.BULK_PREFIX;
        System.arraycopy(lengthBytes, 0, response, pos, lengthBytes.length);
        pos += lengthBytes.length;
        response[pos++] = '\r';
        response[pos++] = '\n';
        System.arraycopy(data, 0, response, pos, data.length);
        pos += data.length;
        response[pos++] = '\r';
        response[pos] = '\n';

        connection.write(response);
    }

    private String formatHumanReadable(long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(java.util.Locale.US, "%.2f%sB", bytes / Math.pow(1024, exp), pre);
    }
}
