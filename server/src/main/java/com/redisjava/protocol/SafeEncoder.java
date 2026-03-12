package com.redisjava.protocol;

/**
 * Utility class for zero-allocation encoding/decoding operations.
 * <p>
 * All methods work directly with byte arrays without creating
 * intermediate String objects, avoiding GC pressure in hot paths.
 * </p>
 */
public final class SafeEncoder {

    private static final byte MINUS = '-';
    private static final byte ZERO = '0';
    private static final byte NINE = '9';

    /**
     * Private constructor - utility class.
     */
    private SafeEncoder() {
    }

    /**
     * Parses a long from a byte array without String allocation.
     * <p>
     * Example: ['-', '1', '2', '3'] → -123L
     * </p>
     *
     * @param data   The byte array containing ASCII digits.
     * @param offset Start position in the array.
     * @param length Number of bytes to parse.
     * @return The parsed long value.
     * @throws NumberFormatException If the bytes don't represent a valid number.
     */
    public static long bytesToLong(byte[] data, int offset, int length) {
        if (data == null || length == 0) {
            throw new NumberFormatException("Empty data");
        }

        boolean negative = false;
        int index = offset;
        int end = offset + length;

        // Check for negative sign
        if (data[index] == MINUS) {
            negative = true;
            index++;
            if (index >= end) {
                throw new NumberFormatException("Invalid number format");
            }
        }

        long result = 0;
        while (index < end) {
            byte b = data[index];
            if (b < ZERO || b > NINE) {
                throw new NumberFormatException("Invalid digit: " + (char) b);
            }
            result = result * 10 + (b - ZERO);
            index++;
        }

        return negative ? -result : result;
    }

    /**
     * Parses an int from a byte array without String allocation.
     *
     * @param data   The byte array containing ASCII digits.
     * @param offset Start position in the array.
     * @param length Number of bytes to parse.
     * @return The parsed int value.
     * @throws NumberFormatException If the bytes don't represent a valid number.
     */
    public static int bytesToInt(byte[] data, int offset, int length) {
        long value = bytesToLong(data, offset, length);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new NumberFormatException("Value out of int range: " + value);
        }
        return (int) value;
    }

    /**
     * Compares two byte arrays for equality.
     * <p>
     * Used for command matching without String conversion.
     * </p>
     *
     * @param a       First array.
     * @param aOffset Start position in first array.
     * @param aLength Length to compare in first array.
     * @param b       Second array.
     * @return true if the specified portions are equal.
     */
    public static boolean equals(byte[] a, int aOffset, int aLength, byte[] b) {
        if (b == null || aLength != b.length) {
            return false;
        }
        for (int i = 0; i < aLength; i++) {
            if (a[aOffset + i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Case-insensitive comparison for command matching.
     * <p>
     * Redis commands are case-insensitive (GET = get = Get).
     * </p>
     *
     * @param a       First array.
     * @param aOffset Start position in first array.
     * @param aLength Length to compare in first array.
     * @param b       Second array (should be uppercase).
     * @return true if the specified portions are equal (case-insensitive).
     */
    public static boolean equalsIgnoreCase(byte[] a, int aOffset, int aLength, byte[] b) {
        if (b == null || aLength != b.length) {
            return false;
        }
        for (int i = 0; i < aLength; i++) {
            byte aByte = a[aOffset + i];
            byte bByte = b[i];

            // Convert lowercase to uppercase for comparison
            if (aByte >= 'a' && aByte <= 'z') {
                aByte = (byte) (aByte - 32);
            }
            if (bByte >= 'a' && bByte <= 'z') {
                bByte = (byte) (bByte - 32);
            }

            if (aByte != bByte) {
                return false;
            }
        }
        return true;
    }
}
