package com.redisjava.memory;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

public class DirectBufferUtils {

    private static final Unsafe UNSAFE;

    static {
        try {

            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(
                    "DirectBufferUtils: Failed to obtain sun.misc.Unsafe! This JVM might not support this operation.",
                    e);
        }
    }

    private DirectBufferUtils() {
    }

    public static long allocate(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Allocation size must be positive: " + size);
        }
        return UNSAFE.allocateMemory(size);
    }

    
    public static void free(long address) {
        if (address == 0) {
            return;
        }
        UNSAFE.freeMemory(address);
    }

    public static void putByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    public static void putInt(long address, int value) {
        UNSAFE.putInt(address, value);
    }

    public static void putLong(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    public static byte getByte(long address) {
        return UNSAFE.getByte(address);
    }

    public static int getInt(long address) {
        return UNSAFE.getInt(address);
    }

    public static long getLong(long address) {
        return UNSAFE.getLong(address);
    }

    public static void copyMemory(long srcAddress, long destAddress, long bytes) {
        UNSAFE.copyMemory(srcAddress, destAddress, bytes);
    }

    public static void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }
}
