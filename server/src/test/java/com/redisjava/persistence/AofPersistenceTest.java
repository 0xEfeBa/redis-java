package com.redisjava.persistence;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * AOF persistans testleri — büyük dosya yükleme ve kısmi yazma yönetimi.
 */
public class AofPersistenceTest {

    private Path tempDir;

    @BeforeEach
    public void setup() throws Exception {
        tempDir = Files.createTempDirectory("aof-test");
        Db.init(new MemoryManager(10));
        Db.getInstance().clear();
    }

    @AfterEach
    public void teardown() {
        Db existing = Db.getInstanceSafe();
        if (existing != null) existing.shutdown();
        // temp dosyaları temizle
        if (tempDir != null) {
            try {
                Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            } catch (Exception ignored) {}
        }
    }

    // ── Büyük AOF yükleme ─────────────────────────────────────────────────

    @Test
    public void testLoadBigAofFile_5000Commands() throws Exception {
        Path aofFile = tempDir.resolve("big.aof");
        int totalCommands = 5000;

        // 5000 SET komutu yaz (~200 KB)
        try (FileChannel channel = FileChannel.open(aofFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
            for (int i = 0; i < totalCommands; i++) {
                String key = "key" + i;
                String val = "val" + i;
                String cmd = String.format("*3\r\n$3\r\nSET\r\n$%d\r\n%s\r\n$%d\r\n%s\r\n",
                        key.length(), key, val.length(), val);
                byte[] bytes = cmd.getBytes();
                if (buf.remaining() < bytes.length) {
                    buf.flip(); channel.write(buf); buf.clear();
                }
                buf.put(bytes);
            }
            buf.flip(); channel.write(buf);
        }

        int loaded = AofLoader.load(aofFile, Db.getInstance());
        assertEquals(totalCommands, loaded);
        assertEquals(totalCommands, Db.getInstance().size());
    }

    // ── Kısmi yazma yönetimi ──────────────────────────────────────────────

    @Test
    public void testPartialWrite_handledCorrectly() throws Exception {
        Path aofPath = tempDir.resolve("partial.aof");
        AofManager.init(aofPath);
        AofManager manager = AofManager.getInstance();

        // Sahte FileChannel: her write() çağrısında en fazla 3 byte yazar
        PartialWriteChannel partialChannel = new PartialWriteChannel(3);
        setField(manager, "channel", partialChannel);
        setField(manager, "enabled", true);

        byte[] key   = "k".getBytes();
        byte[] value = new byte[32 * 1024]; // 32 KB — birden fazla write zorunlu

        manager.appendSet(key, value);

        byte[] written = partialChannel.getWrittenBytes();
        byte[] expected = buildExpectedSetCommand(key, value);

        assertEquals(expected.length, written.length);
        assertEquals('\r', written[written.length - 2]);
        assertEquals('\n', written[written.length - 1]);
        assertTrue(partialChannel.getWriteCalls() > 1, "birden fazla write çağrısı");
    }

    // ── Yardımcı metodlar ─────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static byte[] buildExpectedSetCommand(byte[] key, byte[] value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("*3\r\n".getBytes());
        out.write("$3\r\nSET\r\n".getBytes());
        out.write(("$" + key.length + "\r\n").getBytes());
        out.write(key);
        out.write("\r\n".getBytes());
        out.write(("$" + value.length + "\r\n").getBytes());
        out.write(value);
        out.write("\r\n".getBytes());
        return out.toByteArray();
    }

    // ── Kısmi yazma simülasyon kanalı ─────────────────────────────────────

    private static final class PartialWriteChannel extends FileChannel {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final int maxPerWrite;
        private int writeCalls = 0;

        PartialWriteChannel(int maxPerWrite) { this.maxPerWrite = maxPerWrite; }

        int getWriteCalls()    { return writeCalls; }
        byte[] getWrittenBytes() { return out.toByteArray(); }

        @Override
        public int write(ByteBuffer src) throws java.io.IOException {
            writeCalls++;
            int toWrite = Math.min(src.remaining(), maxPerWrite);
            byte[] data = new byte[toWrite];
            src.get(data);
            out.write(data);
            return toWrite;
        }

        @Override public long write(ByteBuffer[] srcs, int offset, int length)       { throw new UnsupportedOperationException(); }
        @Override public int  write(ByteBuffer src, long position)                   { throw new UnsupportedOperationException(); }
        @Override public int  read(ByteBuffer dst)                                   { return -1; }
        @Override public long read(ByteBuffer[] dsts, int offset, int length)        { throw new UnsupportedOperationException(); }
        @Override public int  read(ByteBuffer dst, long position)                    { throw new UnsupportedOperationException(); }
        @Override public long position()                                             { throw new UnsupportedOperationException(); }
        @Override public FileChannel position(long newPosition)                      { throw new UnsupportedOperationException(); }
        @Override public long size()                                                 { return out.size(); }
        @Override public FileChannel truncate(long size)                             { throw new UnsupportedOperationException(); }
        @Override public void force(boolean metaData)                                {}
        @Override public long transferTo(long position, long count, WritableByteChannel target) { throw new UnsupportedOperationException(); }
        @Override public long transferFrom(ReadableByteChannel src, long position, long count)  { throw new UnsupportedOperationException(); }
        @Override public java.nio.MappedByteBuffer map(MapMode mode, long position, long size)  { throw new UnsupportedOperationException(); }
        @Override public FileLock lock(long position, long size, boolean shared)     { throw new UnsupportedOperationException(); }
        @Override public FileLock tryLock(long position, long size, boolean shared)  { throw new UnsupportedOperationException(); }
        @Override protected void implCloseChannel()                                  {}
    }
}
