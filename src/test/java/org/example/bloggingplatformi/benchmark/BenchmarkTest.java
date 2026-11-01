package org.example.bloggingplatformi.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkTest {

    @Test
    @DisplayName("runs the warm-up and then the measured iterations")
    void runsWarmupThenIterations() {
        // The warm-up exists so the measured runs are of compiled code, not of
        // the interpreter. It must execute, and must not be counted.
        AtomicInteger calls = new AtomicInteger();

        BenchmarkResult result = new Benchmark()
                .warmup(3)
                .iterations(10)
                .measure("counter", "test", calls::incrementAndGet);

        assertEquals(13, calls.get(), "expected 3 warm-up runs plus 10 measured");
        assertEquals(10, result.iterations(), "only the measured runs are reported");
    }

    @Test
    @DisplayName("reports the median, not the mean, as the headline")
    void medianIgnoresOutliers() {
        // One slow iteration, as a GC pause would produce. The mean absorbs it
        // and reports an operation far slower than it is; the median does not.
        AtomicInteger run = new AtomicInteger();

        BenchmarkResult result = new Benchmark()
                .warmup(0)
                .iterations(9)
                .measure("spiky", "test", () -> {
                    if (run.incrementAndGet() == 5) {
                        busyWait(30);
                    } else {
                        busyWait(1);
                    }
                    return run.get();
                });

        assertTrue(result.medianMs() < result.meanMs(),
                "the median should sit below a mean skewed by the outlier: median="
                        + result.medianMs() + " mean=" + result.meanMs());
        assertTrue(result.maxMs() >= result.medianMs());
    }

    @Test
    @DisplayName("min, median and max are ordered")
    void percentilesAreOrdered() {
        BenchmarkResult result = new Benchmark()
                .warmup(1)
                .iterations(12)
                .measure("ordered", "test", () -> busyWaitReturning(1));

        assertTrue(result.minMs() <= result.medianMs(), "min above median");
        assertTrue(result.medianMs() <= result.p95Ms(), "median above p95");
        assertTrue(result.p95Ms() <= result.maxMs(), "p95 above max");
    }

    @Test
    @DisplayName("a single iteration still produces a usable result")
    void singleIteration() {
        BenchmarkResult result = new Benchmark()
                .warmup(0)
                .iterations(1)
                .measure("once", "test", () -> 1);

        assertEquals(1, result.iterations());
        assertEquals(result.minMs(), result.maxMs(), 0.0001);
    }

    @Test
    @DisplayName("speedup is computed from the medians")
    void speedupOverBaseline() {
        BenchmarkResult slow = new BenchmarkResult("op", "slow", 10, 10.0, 10.0, 9, 11, 10.5, 0);
        BenchmarkResult fast = new BenchmarkResult("op", "fast", 10, 2.0, 2.0, 1.9, 2.1, 2.05, 0);

        assertEquals(5.0, fast.speedupOver(slow), 0.001);
    }

    @Test
    @DisplayName("CSV output has a header and one row per result")
    void csvStructure() {
        Benchmark benchmark = new Benchmark().warmup(0).iterations(2);
        benchmark.measure("first", "a", () -> 1);
        benchmark.measure("second", "b", () -> 2);

        List<String> lines = BenchmarkResult.csvLines(benchmark.results());

        assertEquals(3, lines.size());
        assertEquals(BenchmarkResult.CSV_HEADER, lines.get(0));
        assertTrue(lines.get(1).startsWith("first,a,2,"));
        assertTrue(lines.get(2).startsWith("second,b,2,"));
    }

    @Test
    @DisplayName("a label containing a comma is quoted, not left to shift the columns")
    void csvEscapesSeparators() {
        BenchmarkResult result =
                new BenchmarkResult("search, indexed", "n=1,000", 5, 1.0, 1.0, 1.0, 1.0, 1.0, 0);

        String row = result.toCsvRow();

        assertTrue(row.startsWith("\"search, indexed\",\"n=1,000\",5,"),
                "unquoted separators would shift every later column: " + row);
    }

    @Test
    @DisplayName("writeCsv creates the file and any missing directories")
    void writesCsvFile(@TempDir Path tempDir) throws Exception {
        Benchmark benchmark = new Benchmark().warmup(0).iterations(2);
        benchmark.measure("write-test", "variant", () -> 1);

        Path target = benchmark.writeCsv(tempDir.resolve("nested/results.csv"));

        assertTrue(Files.exists(target));
        List<String> lines = Files.readAllLines(target);
        assertEquals(2, lines.size());
        assertEquals(BenchmarkResult.CSV_HEADER, lines.get(0));
    }

    @Test
    @DisplayName("the environment line records what the numbers depend on")
    void environmentIsRecorded() {
        // A timing without the JVM, OS and core count cannot be compared with
        // another machine's, and a report that omits them invites exactly that.
        String environment = Benchmark.environment();

        assertTrue(environment.contains("JVM"));
        assertTrue(environment.contains("cores"));
        assertTrue(environment.contains("heap max"));
    }

    @Test
    @DisplayName("the display table has a header and one line per result")
    void displayTable() {
        Benchmark benchmark = new Benchmark().warmup(0).iterations(1);
        benchmark.measure("display", "variant", () -> 1);

        String[] lines = benchmark.toDisplayTable().split("\\R");

        assertEquals(3, lines.length, "expected a header, a rule and one row");
        assertTrue(lines[0].contains("OPERATION"));
        assertTrue(lines[2].contains("display"));
    }

    /** Spins for roughly the requested milliseconds; Thread.sleep is too coarse. */
    private static void busyWait(long millis) {
        long until = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < until) {
            Thread.onSpinWait();
        }
    }

    private static int busyWaitReturning(long millis) {
        busyWait(millis);
        return 1;
    }
}
