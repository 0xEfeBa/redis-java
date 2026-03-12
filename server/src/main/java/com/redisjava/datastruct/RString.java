package com.redisjava.datastruct;

import com.redisjava.memory.DirectBufferUtils;
import com.redisjava.memory.MemoryManager;

/**
 * Off-heap Redis String implementation.
 * <p>
 * Stores string data in off-heap memory for zero-GC operation.
 * The data is stored as raw bytes, not Java String objects.
 * </p>
 */
public class RString {

    private final long address;
    private final int length;

    /**
     * Creates an RString from a byte array.
     *
     * @param data   The byte data to store.
     * @param memory The memory manager for allocation.
     */
    public RString(byte[] data, MemoryManager memory) {
        this.length = data.length;

        // Handle empty strings without allocation
        if (length == 0) {
            this.address = 0;
            return;
        }

        this.address = memory.alloc(length);

        // Copy data to off-heap
        for (int i = 0; i < length; i++) {
            DirectBufferUtils.putByte(address + i, data[i]);
        }
    }

    /**
     * Creates an RString from existing off-heap address.
     *
     * @param address The off-heap memory address.
     * @param length  The length of the data.
     */
    public RString(long address, int length) {
        this.address = address;
        this.length = length;
    }

    /**
     * Gets a byte at the specified index.
     *
     * @param index The byte index.
     * @return The byte value.
     */
    public byte getByte(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
        }
        return DirectBufferUtils.getByte(address + index);
    }

    /**
     * Reads all bytes into a new array.
     *
     * @return A copy of the data as a byte array.
     */
    public byte[] getBytes() {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = DirectBufferUtils.getByte(address + i);
        }
        return result;
    }

    /**
     * Compares this RString with a byte array.
     *
     * @param other The byte array to compare.
     * @return true if contents are equal.
     */
    public boolean equals(byte[] other) {
        if (other == null || other.length != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (DirectBufferUtils.getByte(address + i) != other[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes hash code from off-heap data.
     *
     * @return Hash code based on content.
     */
    @Override
    public int hashCode() {
        int hash = 1;
        for (int i = 0; i < length; i++) {
            hash = 31 * hash + DirectBufferUtils.getByte(address + i);
        }
        return hash;
    }

    /**
     * @return The off-heap memory address.
     */
    public long getAddress() {
        return address;
    }

    /**
     * @return The length of the data.
     */
    public int getLength() {
        return length;
    }

    /**
     * Frees the off-heap memory.
     *
     * @param memory The memory manager.
     */
    public void free(MemoryManager memory) {
        // Don't free empty strings (they weren't allocated)
        if (length > 0) {
            memory.free(address);
        }
    }

    /**
     * Static factory to create RString from bytes.
     */
    public static RString fromBytes(byte[] data, MemoryManager memory) {
        return new RString(data, memory);
    }
}
