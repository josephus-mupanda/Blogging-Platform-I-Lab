package org.example.bloggingplatformi.benchmark;

import java.util.List;
import java.util.Locale;

/**
 * The timings from one benchmarked operation, in milliseconds.
 *
 * <p><b>The median is the headline, not the mean.</b> A JVM benchmark's
 * outliers are not noise to be averaged in: a garbage collection pause or a
 * JIT recompilation can make one iteration ten times slower than the rest, and
 * a mean reports that artefact as though it were the cost of the operation.
 * The median ignores it. The mean and the maximum are kept alongside so the
 * spread is visible rather than hidden.
 */
public record BenchmarkResult(
        String name,
        String variant,
        int iterations,
        double medianMs,
        double meanMs,
        double minMs,
        double maxMs,
        double p95Ms,
        long checksum
) {

    /** CSV header matching {@link #toCsvRow()}. */
    public static final String CSV_HEADER =
            "operation,variant,iterations,median_ms,mean_ms,min_ms,max_ms,p95_ms";

    /**
     * Builds a result from raw per-iteration timings in nanoseconds.
     *
     * @param checksum a value derived from the work, kept so the JIT cannot
     *                 prove the computation unused and eliminate it entirely
     */
    public static BenchmarkResult from(String name, String variant, long[] nanos, long checksum) {
        long[] sorted = nanos.clone();
        java.util.Arrays.sort(sorted);

        double median = percentile(sorted, 50) / 1_000_000.0;
        double p95 = percentile(sorted, 95) / 1_000_000.0;
        double min = sorted[0] / 1_000_000.0;
        double max = sorted[sorted.length - 1] / 1_000_000.0;

        double sum = 0;
        for (long value : nanos) {
            sum += value;
        }
        double mean = (sum / nanos.length) / 1_000_000.0;

        return new BenchmarkResult(name, variant, nanos.length, median, mean, min, max, p95, checksum);
    }

    private static double percentile(long[] sorted, int percentile) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        // Nearest-rank, clamped. Exact interpolation would imply a precision
        // these sample sizes do not have.
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank))];
    }

    /** How many times faster {@code this} is than {@code baseline}, by median. */
    public double speedupOver(BenchmarkResult baseline) {
        return medianMs == 0 ? Double.POSITIVE_INFINITY : baseline.medianMs / medianMs;
    }

    public String toCsvRow() {
        return String.format(Locale.ENGLISH, "%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f",
                escape(name), escape(variant), iterations, medianMs, meanMs, minMs, maxMs, p95Ms);
    }

    private static String escape(String value) {
        // A comma or quote in a label would otherwise shift every later column.
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** One aligned line, for reading in a terminal. */
    public String toDisplayRow() {
        return String.format(Locale.ENGLISH, "%-38s %-14s %7d  %9.3f  %9.3f  %9.3f",
                truncate(name, 38), truncate(variant, 14), iterations, medianMs, meanMs, maxMs);
    }

    public static String displayHeader() {
        return String.format(Locale.ENGLISH, "%-38s %-14s %7s  %9s  %9s  %9s",
                "OPERATION", "VARIANT", "ITERS", "MEDIAN", "MEAN", "MAX")
             + System.lineSeparator()
             + "-".repeat(94);
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }

    public static List<String> csvLines(List<BenchmarkResult> results) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(CSV_HEADER);
        results.forEach(result -> lines.add(result.toCsvRow()));
        return lines;
    }
}
