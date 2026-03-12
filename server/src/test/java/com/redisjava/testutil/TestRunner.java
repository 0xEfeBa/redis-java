package com.redisjava.testutil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal test runner - annotasyon olmadan çalışır.
 * Test metodları "test" ile başlamalı.
 */
public final class TestRunner {
    private TestRunner() {}

    public static TestResult run(Class<?> testClass) {
        TestResult result = new TestResult(testClass.getSimpleName());
        Method[] methods = testClass.getDeclaredMethods();

        for (Method m : methods) {
            if (!m.getName().startsWith("test")) continue;
            try {
                Object instance = testClass.getDeclaredConstructor().newInstance();
                // setup if exists
                try { testClass.getMethod("setup").invoke(instance); } catch (NoSuchMethodException ignored) {}
                m.invoke(instance);
                result.pass(m.getName());
            } catch (java.lang.reflect.InvocationTargetException e) {
                result.fail(m.getName(), e.getCause());
            } catch (Exception e) {
                result.fail(m.getName(), e);
            }
        }
        return result;
    }

    public static class TestResult {
        private final String name;
        private int passed = 0, failed = 0;
        private final List<String> failures = new ArrayList<>();

        TestResult(String name) { this.name = name; }

        void pass(String method) { passed++; System.out.println("  ✓ " + method); }
        void fail(String method, Throwable t) {
            failed++;
            String msg = "  ✗ " + method + " → " + t.getMessage();
            failures.add(msg);
            System.out.println(msg);
        }

        public void print() {
            System.out.printf("[%s] %d passed, %d failed%n", name, passed, failed);
        }

        public boolean isSuccess() { return failed == 0; }
        public int getPassed() { return passed; }
        public int getFailed() { return failed; }
    }

    /** Birden fazla test sınıfı çalıştır */
    @SafeVarargs
    public static void runAll(Class<?>... classes) {
        int total = 0, totalFailed = 0;
        for (Class<?> c : classes) {
            TestResult r = run(c);
            r.print();
            total += r.getPassed() + r.getFailed();
            totalFailed += r.getFailed();
        }
        System.out.printf("%n=== TOPLAM: %d test, %d başarılı, %d başarısız ===%n",
            total, total - totalFailed, totalFailed);
        if (totalFailed > 0) System.exit(1);
    }
}
