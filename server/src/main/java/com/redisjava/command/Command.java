package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * Interface for all Redis commands.
 * <p>
 * Each command implementation handles a specific Redis operation
 * (PING, GET, SET, etc.) and writes the response to the connection.
 * </p>
 */
public interface Command {

    /**
     * Executes the command with the given arguments.
     *
     * @param connection The client connection to write response to.
     * @param args       The command arguments (first element is command name).
     */
    void execute(Connection connection, RespToken[] args);
}
