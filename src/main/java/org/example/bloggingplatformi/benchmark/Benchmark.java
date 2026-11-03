package org.example.bloggingplatformi.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A small benchmark tool: run a few warm-up rounds, repeat the operation, and
 * report the median time.
 *
 * Warm-up lets the JIT compile the code first. The median ignores one slow run
 * caused by garbage collection. Each result goes into a checksum so the JIT
 * cannot remove the work as unused. This is simpler than JMH, but good enough
 * to compare an indexed query with an unindexed one.
 */
public final class Benchmark {

    public static final int DEFAULT_WARMUP = 5;
    public static final int DEFAULT_ITERATIONS = 20;

    private final List<BenchmarkResult> results = new ArrayList<>();
    private int warmup = DEFAULT_WARMUP;
    private int iterations = DEFAULT_ITERATIONS;

    public Benchmark warmup(int runs) {
        this.warmup = Math.max(0, runs);
        return this;
    }

    public Benchmark iterations(int runs) {
        this.iterations = Math.max(1, runs);
        return this;
    }

    /**
     * Times the operation. Warm-up runs are not counted.
     *
     * @param operation should return a value from its work, so the JIT cannot skip it
     */
    public BenchmarkResult measure(String name, String variant, Supplier<?> operation) {
        for (int i = 0; i < warmup; i++) {
            consume(operation.get());
        }

        long[] nanos = new long[iterations];
        long checksum = 0;

        for (int i = 0; i < iterations; i++) {
            // nanoTime is used because currentTimeMillis is only accurate to about 15ms on Windows.
            long start = System.nanoTime();
            Object value = operation.get();
            nanos[i] = System.nanoTime() - start;
            checksum += consume(value);
        }

        BenchmarkResult result = BenchmarkResult.from(name, variant, nanos, checksum);
        results.add(result);
        return result;
    }

    /** Times an operation with no natural return value. */
    public BenchmarkResult measure(String name, String variant, Runnable operation) {
        return measure(name, variant, () -> {
            operation.run();
            return 1;
        });
    }

    /**
     * Turns a result into a number for the checksum.
     * For collections we use the size, because hashing a big list would be slow.
     */
    private static long consume(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof java.util.Map<?, ?> map) {
            return map.size();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        if (value instanceof java.util.Optional<?> optional) {
            return optional.isPresent() ? 1 : 0;
        }
        return 1;
    }

    public List<BenchmarkResult> results() {
        return List.copyOf(results);
    }

    public void clear() {
        results.clear();
    }

    /** A text table of all results so far. */
    public String toDisplayTable() {
        StringBuilder table = new StringBuilder(BenchmarkResult.displayHeader());
        for (BenchmarkResult result : results) {
            table.append(System.lineSeparator()).append(result.toDisplayRow());
        }
        return table.toString();
    }

    /** Writes the results as CSV, creating parent directories as needed. */
    public Path writeCsv(Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, BenchmarkResult.csvLines(results), StandardCharsets.UTF_8);
        return target;
    }

    /** Describes the JVM, OS, cores and memory, because timings depend on them. */
    public static String environment() {
        Runtime runtime = Runtime.getRuntime();
        return "JVM " + System.getProperty("java.version")
             + " (" + System.getProperty("java.vm.name") + ")"
             + ", OS " + System.getProperty("os.name") + " " + System.getProperty("os.version")
             + ", " + runtime.availableProcessors() + " cores"
             + ", heap max " + (runtime.maxMemory() / 1024 / 1024) + " MB";
    }
}
