package com.redisjava.protocol;

import com.redisjava.command.Command;
import com.redisjava.command.CommandRegistry;
import com.redisjava.command.Responses;
import com.redisjava.datastruct.Db;
import com.redisjava.network.Connection;
import com.redisjava.network.ProtocolHandler;

import java.nio.ByteBuffer;
import java.util.WeakHashMap;

import com.redisjava.persistence.TtlReaper;
import com.redisjava.stats.ServerStats;

/**
 * Redis protocol handler - bridges network layer with RESP parsing.
 * <p>
 * Implements {@link ProtocolHandler} to receive raw bytes from
 * the network layer, parse them into Redis commands, and dispatch
 * to the appropriate command handler.
 * </p>
 * <p>
 * Each connection gets its own RespParser to handle partial reads correctly.
 * </p>
 */
public class RedisProtocolHandler implements ProtocolHandler {

    /**
     * Per-connection parser map.
     * WeakHashMap ensures automatic cleanup when Connection is garbage collected.
     */
    private final WeakHashMap<Connection, RespParser> parsers = new WeakHashMap<>();
    private final CommandRegistry commandRegistry = new CommandRegistry();

    /**
     * Gets or creates a parser for the given connection.
     *
     * @param connection The connection.
     * @return Parser instance for this connection.
     */
    private RespParser getParser(Connection connection) {
        RespParser parser = parsers.get(connection);
        if (parser == null) {
            parser = new RespParser();
            parsers.put(connection, parser);
        }
        return parser;
    }

    /**
     * Handles incoming data from a connection.
     * <p>
     * Parses RESP data and dispatches complete commands.
     * </p>
     *
     * @param connection The connection that received data.
     * @param buffer     The read buffer containing incoming data.
     */
    @Override
    public void handle(Connection connection, ByteBuffer buffer) {
        try {
            RespParser parser = getParser(connection);
            RespToken token = parser.parse(buffer);

            if (token == null) {
                return; // Incomplete frame — wait for more data
            }

            // Pipelining: accumulate all responses for this read into one flush.
            // beginBatch() defers OP_WRITE registration; endBatch() registers it once.
            connection.beginBatch();
            try {
                while (token != null) {
                    processCommand(connection, token);
                    token = parser.parse(buffer);
                }
            } finally {
                connection.endBatch();
            }
        } catch (RuntimeException e) {
            // Protocol error or malformed input; protect the event loop.
            try {
                connection.write(Responses.ERR_SYNTAX);
            } catch (Exception ignored) {
            }
            connection.close();
        }
    }

    /**
     * Processes a parsed command by dispatching to the command registry.
     *
     * @param connection The connection to respond to.
     * @param command    The parsed command token (should be an array).
     */
    private void processCommand(Connection connection, RespToken command) {
        // Commands are RESP arrays: *N\r\n$len\r\nCMD\r\n...
        if (command.getType() != RespToken.Type.ARRAY || command.getArrayLength() == 0) {
            connection.write(Responses.ERR_SYNTAX);
            return;
        }

        RespToken[] args = command.getArrayElements();
        RespToken cmdToken = args[0];

        if (cmdToken.getType() != RespToken.Type.BULK_STRING) {
            connection.write(Responses.ERR_SYNTAX);
            return;
        }

        byte[] cmdName = cmdToken.getData();
        Command cmd = commandRegistry.lookup(cmdName, 0, cmdName.length);

        if (cmd != null) {
            ServerStats.getInstance().commandExecuted();
            cmd.execute(connection, args);
        } else {
            connection.write(Responses.ERR_UNKNOWN_COMMAND);
        }
    }

    @Override
    public void onConnect(Connection connection) {
        ServerStats.getInstance().connectionOpened();
        // Parser will be created lazily on first data
    }

    @Override
    public void onDisconnect(Connection connection) {
        ServerStats.getInstance().connectionClosed();
        // Explicit cleanup (WeakHashMap will also handle this automatically)
        parsers.remove(connection);
    }

    @Override
    public void onCron(long now) {
        // Delegate to the adaptive TTL reaper (handles its own DB null-check)
        TtlReaper.getInstance().runCycle(now);
    }
}
