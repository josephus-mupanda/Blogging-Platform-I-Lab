package org.example.bloggingplatformi.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A small benchmark harness: warm up, repeat, report the median.
 *
 * <p>Three mistakes this exists to avoid, all of which produce numbers that
 * look authoritative and are wrong:
 *
 * <ol>
 *   <li><b>No warm-up.</b> The first runs of any Java method are interpreted;
 *       HotSpot only compiles it after a few thousand invocations. Timing
 *       without a warm-up measures the interpreter and the JIT, not the code.</li>
 *   <li><b>Reporting a mean.</b> One GC pause makes a single iteration an order
 *       of magnitude slower, and a mean folds that artefact into the result.
 *       {@link BenchmarkResult} reports the median and keeps the mean and max
 *       beside it so the spread stays visible.</li>
 *   <li><b>Dead-code elimination.</b> If a result is never used, the JIT is
 *       entitled to delete the computation entirely and the benchmark then
 *       measures an empty loop. Every operation here returns a value that is
 *       folded into a checksum the harness keeps.</li>
 * </ol>
 *
 * <p>This is not JMH. JMH additionally handles loop unrolling, constant
 * folding, false sharing and on-stack replacement, and anything published as a
 * precise claim should use it. For comparing an indexed query against an
 * unindexed one — where the difference is a factor, not a few percent — this is
 * sufficient, and that limitation belongs in the report.
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
     * Times {@code operation}, discarding the warm-up runs.
     *
     * @param operation must return something derived from its work; the value
     *                  feeds the checksum that stops the JIT eliminating it
     */
    public BenchmarkResult measure(String name, String variant, Supplier<?> operation) {
        for (int i = 0; i < warmup; i++) {
            consume(operation.get());
        }

        long[] nanos = new long[iterations];
        long checksum = 0;

        for (int i = 0; i < iterations; i++) {
            // nanoTime, never currentTimeMillis: the latter has a resolution
            // of about 15ms on Windows, which is longer than most of what is
            // being measured here.
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
     * Folds a value into a number the JIT cannot discard.
     *
     * <p>Collections report their size rather than their hash: hashing a large
     * list costs more than some of the operations being measured and would
     * dominate the timing.
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

    /** Aligned table of every result so far. */
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

    /**
     * Records the environment a run happened in.
     *
     * <p>A timing without this is not reproducible: the same benchmark on a
     * different JVM, core count or operating system is a different number, and
     * a report that omits them invites its figures to be compared with ones
     * they cannot be compared to.
     */
    public static String environment() {
        Runtime runtime = Runtime.getRuntime();
        return "JVM " + System.getProperty("java.version")
             + " (" + System.getProperty("java.vm.name") + ")"
             + ", OS " + System.getProperty("os.name") + " " + System.getProperty("os.version")
             + ", " + runtime.availableProcessors() + " cores"
             + ", heap max " + (runtime.maxMemory() / 1024 / 1024) + " MB";
    }
}
