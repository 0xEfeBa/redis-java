package com.redisjava.datastruct;

import com.redisjava.memory.DirectBufferUtils;
import com.redisjava.memory.MemoryManager;

/**
 * Simplified ZipList V2 stored in a single off-heap block.
 * Header layout: [ZLBYTES(4)][ZLTAIL(4)][ZLLEN(2)]
 * Entry layout: [LEN(4)][DATA...]
 */
public class ZipList {

    private static final int ZLBYTES_OFFSET = 0;
    private static final int ZLTAIL_OFFSET = 4;
    private static final int ZLLEN_OFFSET = 8;
    private static final int HEADER_SIZE = 10;

    private final MemoryManager memory;
    private long address;
    private int capacity;

    public ZipList(MemoryManager memory) {
        this(memory, 64);
    }

    public ZipList(MemoryManager memory, int initialCapacity) {
        if (memory == null) {
            throw new IllegalArgumentException("MemoryManager required");
        }
        if (initialCapacity < HEADER_SIZE) {
            initialCapacity = HEADER_SIZE;
        }
        this.memory = memory;
        this.capacity = initialCapacity;
        this.address = memory.alloc(initialCapacity);
        initHeader();
    }

    public void add(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        int usedBytes = getUsedBytes();
        int entrySize = 4 + data.length;
        int needed = usedBytes + entrySize;
        ensureCapacity(needed);

        int entryOffset = usedBytes;
        DirectBufferUtils.putInt(address + entryOffset, data.length);
        long dataAddress = address + entryOffset + 4;
        for (int i = 0; i < data.length; i++) {
            DirectBufferUtils.putByte(dataAddress + i, data[i]);
        }

        setUsedBytes(needed);
        setTailOffset(entryOffset);
        setLen(getLen() + 1);
    }

    public ZipListCursor cursor() {
        return new ZipListCursor(this);
    }

    public void free(MemoryManager memoryManager) {
        memoryManager.free(address);
        address = 0;
        capacity = 0;
    }

    int getUsedBytes() {
        return DirectBufferUtils.getInt(address + ZLBYTES_OFFSET);
    }

    int getTailOffset() {
        return DirectBufferUtils.getInt(address + ZLTAIL_OFFSET);
    }

    int getLen() {
        return readUnsignedShort(address + ZLLEN_OFFSET);
    }

    long getAddress() {
        return address;
    }

    int getHeaderSize() {
        return HEADER_SIZE;
    }

    int getEntryLength(int offset) {
        return DirectBufferUtils.getInt(address + offset);
    }

    long getEntryDataAddress(int offset) {
        return address + offset + 4;
    }

    private void initHeader() {
        setUsedBytes(HEADER_SIZE);
        setTailOffset(HEADER_SIZE);
        setLen(0);
    }

    private void setUsedBytes(int bytes) {
        DirectBufferUtils.putInt(address + ZLBYTES_OFFSET, bytes);
    }

    private void setTailOffset(int offset) {
        DirectBufferUtils.putInt(address + ZLTAIL_OFFSET, offset);
    }

    private void setLen(int len) {
        if (len < 0 || len > 0xffff) {
            throw new IllegalStateException("ZipList length overflow");
        }
        writeUnsignedShort(address + ZLLEN_OFFSET, (short) len);
    }

    private void ensureCapacity(int needed) {
        if (needed <= capacity) {
            return;
        }
        int newCapacity = Math.max(needed, capacity * 2);
        address = memory.realloc(address, capacity, newCapacity);
        capacity = newCapacity;
    }

    private short readUnsignedShort(long addr) {
        int b0 = DirectBufferUtils.getByte(addr) & 0xff;
        int b1 = DirectBufferUtils.getByte(addr + 1) & 0xff;
        return (short) ((b0 << 8) | b1);
    }

    private void writeUnsignedShort(long addr, short value) {
        DirectBufferUtils.putByte(addr, (byte) ((value >>> 8) & 0xff));
        DirectBufferUtils.putByte(addr + 1, (byte) (value & 0xff));
    }
}
