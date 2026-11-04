package org.example.bloggingplatformi.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.example.bloggingplatformi.db.AppConfig;
import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.util.Sorting;

/**
 * Measures the speed before and after each optimisation, for docs/performance-report.md.
 *
 * Run it on the big benchmark database (100 000 posts, see db/bench/scale.sql):
 *   mvn javafx:run@bench
 * It prints a table; the numbers are copied into the performance report.
 */
public class Benchmark {

    /** Each query runs this many times and we keep the average. */
    private static final int RUNS = 20;

    // The same queries the app runs.
    private static final String SEARCH = "SELECT COUNT(*) FROM posts WHERE title ILIKE ? OR body ILIKE ?";
    private static final String BY_AUTHOR = "SELECT COUNT(*) FROM posts WHERE author_id = ?";
    private static final String NEWEST_PAGE = "SELECT * FROM posts ORDER BY created_at DESC LIMIT 10";

    public static void main(String[] args) throws Exception {
        // The benchmark deletes and creates indexes, so it must never run on the real database.
        if (!AppConfig.get("db.postgres.url").contains("bench")) {
            System.out.println("Run this on the benchmark database only: mvn javafx:run@bench");
            return;
        }

        System.out.println("| What | Before | After |");
        System.out.println("|---|---:|---:|");

        // 0. Connection: 20 queries opening a new connection each time,
        //    then 20 queries on the one connection the app reuses (Database.connection()).
        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            try (Connection fresh = DriverManager.getConnection(AppConfig.get("db.postgres.url"),
                    AppConfig.get("db.postgres.user"), AppConfig.get("db.postgres.password"))) {
                fresh.createStatement().executeQuery("SELECT 1").close();
            }
        }
        double newConnections = millisSince(start);
        start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            Database.connection().createStatement().executeQuery("SELECT 1").close();
        }
        print("20 queries (new connection each time -> one reused connection)", newConnections, millisSince(start));

        // 1. Indexes: the same queries without and with the indexes of db/postgres/indexes.sql.
        Connection connection = Database.connection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS idx_posts_author_id, idx_posts_title_trgm, "
                    + "idx_posts_body_trgm, idx_post_tags_tag_id, idx_posts_created_at");
            double searchBefore = time(connection, SEARCH, "%number 77777%");
            double authorBefore = time(connection, BY_AUTHOR, 42L);
            double pageBefore = time(connection, NEWEST_PAGE, null);

            statement.execute(Files.readString(Path.of("db/postgres/indexes.sql")));
            statement.execute("ANALYZE posts");
            double searchAfter = time(connection, SEARCH, "%number 77777%");
            double authorAfter = time(connection, BY_AUTHOR, 42L);
            double pageAfter = time(connection, NEWEST_PAGE, null);

            print("Keyword search (trigram index)", searchBefore, searchAfter);
            print("Posts of one author (author_id index)", authorBefore, authorAfter);
            print("Newest 10 posts (created_at index)", pageBefore, pageAfter);
        }

        // 2. Cache: read the same 1 000 posts twice. The first time they come from
        //    PostgreSQL, the second time from the HashMap in PostService.
        PostService postService = new PostService();
        start = System.nanoTime();
        for (long id = 1; id <= 1000; id++) {
            postService.findById(id);
        }
        double firstRead = millisSince(start);
        start = System.nanoTime();
        for (long id = 1; id <= 1000; id++) {
            postService.findById(id);
        }
        double secondRead = millisSince(start);
        print("Read 1 000 posts (cache)", firstRead, secondRead);

        // 3. Searching: 1 000 searches in a sorted list of 100 000 numbers.
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            numbers.add(i * 2);
        }
        start = System.nanoTime();
        for (int key = 0; key < 1000; key++) {
            Sorting.linearSearch(numbers, key * 199, Comparator.naturalOrder());
        }
        double linear = millisSince(start);
        start = System.nanoTime();
        for (int key = 0; key < 1000; key++) {
            Sorting.binarySearch(numbers, key * 199, Comparator.naturalOrder());
        }
        double binary = millisSince(start);
        print("1 000 searches in 100 000 numbers (linear -> binary search)", linear, binary);
        Database.close();
    }

    /** Runs the query once to warm up, then RUNS times, and returns the average time in ms. */
    private static double time(Connection connection, String sql, Object value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int placeholders = statement.getParameterMetaData().getParameterCount();
            for (int i = 1; i <= placeholders; i++) {
                statement.setObject(i, value);
            }
            statement.executeQuery().close();
            long start = System.nanoTime();
            for (int run = 0; run < RUNS; run++) {
                statement.executeQuery().close();
            }
            return millisSince(start) / RUNS;
        }
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private static void print(String what, double before, double after) {
        System.out.printf("| %s | %.2f ms | %.2f ms |%n", what, before, after);
    }
}
