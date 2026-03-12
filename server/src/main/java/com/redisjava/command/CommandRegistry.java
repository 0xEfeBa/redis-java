package com.redisjava.command;

import com.redisjava.protocol.SafeEncoder;

/**
 * Command registry with first-byte switch lookup.
 * <p>
 * Uses O(1) switch dispatch based on first character of command name,
 * followed by byte array comparison. Zero-allocation lookup.
 * </p>
 */
public class CommandRegistry {

    // Pre-allocated command name bytes
    private static final byte[] PING = "PING".getBytes();
    private static final byte[] ECHO = "ECHO".getBytes();
    private static final byte[] INFO = "INFO".getBytes();
    private static final byte[] SET = "SET".getBytes();
    private static final byte[] GET = "GET".getBytes();
    private static final byte[] DEL = "DEL".getBytes();
    private static final byte[] HSET = "HSET".getBytes();
    private static final byte[] HGET = "HGET".getBytes();
    private static final byte[] HDEL = "HDEL".getBytes();
    private static final byte[] HEXISTS = "HEXISTS".getBytes();
    private static final byte[] HGETALL = "HGETALL".getBytes();
    private static final byte[] HLEN = "HLEN".getBytes();
    private static final byte[] INCR = "INCR".getBytes();
    private static final byte[] INCRBY = "INCRBY".getBytes();
    private static final byte[] DECR = "DECR".getBytes();
    private static final byte[] DECRBY = "DECRBY".getBytes();
    private static final byte[] EXISTS = "EXISTS".getBytes();
    private static final byte[] SETNX = "SETNX".getBytes();
    private static final byte[] FLUSHALL = "FLUSHALL".getBytes();
    private static final byte[] EXPIRE = "EXPIRE".getBytes();
    private static final byte[] TTL = "TTL".getBytes();
    private static final byte[] PERSIST = "PERSIST".getBytes();
    // LIST commands
    private static final byte[] LPUSH  = "LPUSH".getBytes();
    private static final byte[] RPUSH  = "RPUSH".getBytes();
    private static final byte[] LPOP   = "LPOP".getBytes();
    private static final byte[] RPOP   = "RPOP".getBytes();
    private static final byte[] LLEN   = "LLEN".getBytes();
    private static final byte[] LRANGE = "LRANGE".getBytes();
    // ZSET commands
    private static final byte[] ZADD             = "ZADD".getBytes();
    private static final byte[] ZREM             = "ZREM".getBytes();
    private static final byte[] ZRANK            = "ZRANK".getBytes();
    private static final byte[] ZRANGE           = "ZRANGE".getBytes();
    private static final byte[] ZSCORE           = "ZSCORE".getBytes();
    private static final byte[] ZCARD            = "ZCARD".getBytes();
    private static final byte[] ZRANGEBYSCORE    = "ZRANGEBYSCORE".getBytes();
    private static final byte[] ZREMRANGEBYSCORE = "ZREMRANGEBYSCORE".getBytes();
    private static final byte[] ZCOUNT           = "ZCOUNT".getBytes();
    // Ticketmaster
    private static final byte[] TICKET_BUY = "TICKET.BUY".getBytes();
    // Pub/Sub
    private static final byte[] SUBSCRIBE   = "SUBSCRIBE".getBytes();
    private static final byte[] UNSUBSCRIBE = "UNSUBSCRIBE".getBytes();
    private static final byte[] PUBLISH     = "PUBLISH".getBytes();
    // Bloom Filter
    private static final byte[] BF_ADD     = "BF.ADD".getBytes();
    private static final byte[] BF_EXISTS  = "BF.EXISTS".getBytes();
    private static final byte[] BF_RESERVE = "BF.RESERVE".getBytes();
    // HyperLogLog
    private static final byte[] PFADD   = "PFADD".getBytes();
    private static final byte[] PFCOUNT = "PFCOUNT".getBytes();
    private static final byte[] PFMERGE = "PFMERGE".getBytes();

    // Singleton command instances
    private final Command pingCommand = new PingCommand();
    private final Command echoCommand = new EchoCommand();
    private final Command infoCommand = new InfoCommand();
    private final Command setCommand = new SetCommand();
    private final Command getCommand = new GetCommand();
    private final Command delCommand = new DelCommand();
    private final Command incrCommand = new IncrCommand();
    private final Command incrByCommand = new IncrByCommand();
    private final Command decrCommand = new DecrCommand();
    private final Command decrByCommand = new DecrByCommand();
    private final Command hsetCommand = new HSetCommand();
    private final Command hgetCommand = new HGetCommand();
    private final Command hdelCommand = new HDelCommand();
    private final Command hexistsCommand = new HExistsCommand();
    private final Command hgetallCommand = new HGetAllCommand();
    private final Command hlenCommand = new HLenCommand();
    private final Command existsCommand = new ExistsCommand();
    // private final Command setNxCommand = new SetNxCommand(); // Removed, handled
    // by SetCommand
    private final Command flushAllCommand = new FlushAllCommand();
    private final Command expireCommand = new ExpireCommand();
    private final Command ttlCommand = new TtlCommand();
    private final Command persistCommand = new PersistCommand();
    // LIST commands
    private final Command lpushCommand  = new LPushCommand();
    private final Command rpushCommand  = new RPushCommand();
    private final Command lpopCommand   = new LPopCommand();
    private final Command rpopCommand   = new RPopCommand();
    private final Command llenCommand   = new LLenCommand();
    private final Command lrangeCommand = new LRangeCommand();
    // Ticketmaster
    private final Command ticketBuyCommand = new TicketBuyCommand();
    // Pub/Sub
    private final Command subscribeCommand   = new SubscribeCommand();
    private final Command unsubscribeCommand = new UnsubscribeCommand();
    private final Command publishCommand     = new PublishCommand();
    // ZSET commands
    private final Command zaddCommand              = new ZAddCommand();
    private final Command zremCommand              = new ZRemCommand();
    private final Command zrankCommand             = new ZRankCommand();
    private final Command zrangeCommand            = new ZRangeCommand();
    private final Command zscoreCommand            = new ZScoreCommand();
    private final Command zcardCommand             = new ZCardCommand();
    private final Command zrangeByScoreCommand    = new ZRangeByScoreCommand();
    private final Command zremrangeByScoreCommand = new ZRemRangeByScoreCommand();
    private final Command zcountCommand            = new ZCountCommand();
    // Bloom Filter commands
    private final Command bfAddCommand     = new BfAddCommand();
    private final Command bfExistsCommand  = new BfExistsCommand();
    private final Command bfReserveCommand = new BfReserveCommand();
    // HyperLogLog commands
    private final Command pfAddCommand   = new PfAddCommand();
    private final Command pfCountCommand = new PfCountCommand();
    private final Command pfMergeCommand = new PfMergeCommand();

    /**
     * Creates a new command registry.
     */
    public CommandRegistry() {
        // Commands initialized above
    }

    /**
     * Looks up a command by name using first-byte switch.
     *
     * @param cmdName Command name bytes.
     * @param offset  Start offset in array.
     * @param length  Length of command name.
     * @return Command instance, or null if unknown.
     */
    public Command lookup(byte[] cmdName, int offset, int length) {
        if (length == 0) {
            return null;
        }

        // Get first byte and convert to uppercase
        byte first = toUpper(cmdName[offset]);

        switch (first) {
            case 'P':
                if (matches(cmdName, offset, length, PING)) {
                    return pingCommand;
                }
                if (matches(cmdName, offset, length, PERSIST)) {
                    return persistCommand;
                }
                if (matches(cmdName, offset, length, PUBLISH)) {
                    return publishCommand;
                }
                if (matches(cmdName, offset, length, PFADD)) {
                    return pfAddCommand;
                }
                if (matches(cmdName, offset, length, PFCOUNT)) {
                    return pfCountCommand;
                }
                if (matches(cmdName, offset, length, PFMERGE)) {
                    return pfMergeCommand;
                }
                break;

            case 'E':
                if (matches(cmdName, offset, length, ECHO)) {
                    return echoCommand;
                }
                if (matches(cmdName, offset, length, EXISTS)) {
                    return existsCommand;
                }
                if (matches(cmdName, offset, length, EXPIRE)) {
                    return expireCommand;
                }
                break;

            case 'I':
                if (matches(cmdName, offset, length, INFO)) {
                    return infoCommand;
                }
                if (matches(cmdName, offset, length, INCR)) {
                    return incrCommand;
                }
                if (matches(cmdName, offset, length, INCRBY)) {
                    return incrByCommand;
                }
                break;

            case 'S':
                if (matches(cmdName, offset, length, SET)) {
                    return setCommand;
                }
                if (matches(cmdName, offset, length, SETNX)) {
                    return setCommand; // Map SETNX to SetCommand
                }
                if (matches(cmdName, offset, length, SUBSCRIBE)) {
                    return subscribeCommand;
                }
                break;

            case 'U':
                if (matches(cmdName, offset, length, UNSUBSCRIBE)) {
                    return unsubscribeCommand;
                }
                break;

            case 'G':
                if (matches(cmdName, offset, length, GET)) {
                    return getCommand;
                }
                break;

            case 'F':
                if (matches(cmdName, offset, length, FLUSHALL)) {
                    return flushAllCommand;
                }
                break;

            case 'D':
                if (matches(cmdName, offset, length, DEL)) {
                    return delCommand;
                }
                if (matches(cmdName, offset, length, DECR)) {
                    return decrCommand;
                }
                if (matches(cmdName, offset, length, DECRBY)) {
                    return decrByCommand;
                }
                break;

            case 'H':
                // Check all H* commands
                if (matches(cmdName, offset, length, HSET)) {
                    return hsetCommand;
                }
                if (matches(cmdName, offset, length, HGET)) {
                    return hgetCommand;
                }
                if (matches(cmdName, offset, length, HDEL)) {
                    return hdelCommand;
                }
                if (matches(cmdName, offset, length, HGETALL)) {
                    return hgetallCommand;
                }
                if (matches(cmdName, offset, length, HEXISTS)) {
                    return hexistsCommand;
                }
                if (matches(cmdName, offset, length, HLEN)) {
                    return hlenCommand;
                }
                break;

            case 'T':
                if (matches(cmdName, offset, length, TTL)) {
                    return ttlCommand;
                }
                if (matches(cmdName, offset, length, TICKET_BUY)) {
                    return ticketBuyCommand;
                }
                break;

            case 'L':
                if (matches(cmdName, offset, length, LPUSH)) {
                    return lpushCommand;
                }
                if (matches(cmdName, offset, length, LPOP)) {
                    return lpopCommand;
                }
                if (matches(cmdName, offset, length, LLEN)) {
                    return llenCommand;
                }
                if (matches(cmdName, offset, length, LRANGE)) {
                    return lrangeCommand;
                }
                break;

            case 'R':
                if (matches(cmdName, offset, length, RPUSH)) {
                    return rpushCommand;
                }
                if (matches(cmdName, offset, length, RPOP)) {
                    return rpopCommand;
                }
                break;

            case 'Z':
                if (matches(cmdName, offset, length, ZADD)) {
                    return zaddCommand;
                }
                if (matches(cmdName, offset, length, ZREM)) {
                    return zremCommand;
                }
                if (matches(cmdName, offset, length, ZRANK)) {
                    return zrankCommand;
                }
                // ZRANGEBYSCORE / ZREMRANGEBYSCORE before ZRANGE (longer prefix first)
                if (matches(cmdName, offset, length, ZRANGEBYSCORE)) {
                    return zrangeByScoreCommand;
                }
                if (matches(cmdName, offset, length, ZREMRANGEBYSCORE)) {
                    return zremrangeByScoreCommand;
                }
                if (matches(cmdName, offset, length, ZRANGE)) {
                    return zrangeCommand;
                }
                if (matches(cmdName, offset, length, ZSCORE)) {
                    return zscoreCommand;
                }
                if (matches(cmdName, offset, length, ZCARD)) {
                    return zcardCommand;
                }
                if (matches(cmdName, offset, length, ZCOUNT)) {
                    return zcountCommand;
                }
                break;


            case 'B':
                if (matches(cmdName, offset, length, BF_ADD)) {
                    return bfAddCommand;
                }
                if (matches(cmdName, offset, length, BF_EXISTS)) {
                    return bfExistsCommand;
                }
                if (matches(cmdName, offset, length, BF_RESERVE)) {
                    return bfReserveCommand;
                }
                break;
        }

        return null;
    }

    /**
     * Converts byte to uppercase.
     */
    private static byte toUpper(byte b) {
        if (b >= 'a' && b <= 'z') {
            return (byte) (b - 32);
        }
        return b;
    }

    /**
     * Case-insensitive byte array comparison.
     */
    private static boolean matches(byte[] cmd, int offset, int length, byte[] target) {
        return SafeEncoder.equalsIgnoreCase(cmd, offset, length, target);
    }
}
