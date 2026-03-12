package com.redisjava.testutil;

/**
 * Minimal assertion library - JUnit bağımlılığı olmadan.
 */
public final class Assert {
    private Assert() {}

    public static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: " + expected + "\n  Actual: " + actual);
    }

    public static void assertEquals(long expected, long actual) {
        if (expected != actual)
            throw new AssertionError("Expected: " + expected + "\n  Actual: " + actual);
    }

    public static void assertEquals(int expected, int actual) {
        if (expected != actual)
            throw new AssertionError("Expected: " + expected + "\n  Actual: " + actual);
    }

    public static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta)
            throw new AssertionError("Expected: " + expected + " (±" + delta + ")\n  Actual: " + actual);
    }

    public static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("Expected true but was false");
    }

    public static void assertTrue(String msg, boolean condition) {
        if (!condition) throw new AssertionError(msg);
    }

    public static void assertFalse(boolean condition) {
        if (condition) throw new AssertionError("Expected false but was true");
    }

    public static void assertFalse(String msg, boolean condition) {
        if (condition) throw new AssertionError(msg);
    }

    public static void assertNull(Object obj) {
        if (obj != null) throw new AssertionError("Expected null but was: " + obj);
    }

    public static void assertNotNull(Object obj) {
        if (obj == null) throw new AssertionError("Expected non-null but was null");
    }

    public static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (expected == null && actual == null) return;
        if (expected == null || actual == null)
            throw new AssertionError("One array is null");
        if (expected.length != actual.length)
            throw new AssertionError("Array length: expected=" + expected.length + " actual=" + actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i])
                throw new AssertionError("Array differs at [" + i + "]: expected=" + expected[i] + " actual=" + actual[i]);
        }
    }
}
