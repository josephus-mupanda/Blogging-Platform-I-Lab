package org.example.bloggingplatformi.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mongodb.client.model.Filters;

import org.bson.Document;

import org.example.bloggingplatformi.algo.InvertedIndex;
import org.example.bloggingplatformi.algo.Sorting;
import org.example.bloggingplatformi.dao.CachedPostDao;
import org.example.bloggingplatformi.dao.CommentDao;
import org.example.bloggingplatformi.dao.JdbcCommentDao;
import org.example.bloggingplatformi.dao.JdbcPostDao;
import org.example.bloggingplatformi.dao.JdbcReviewDao;
import org.example.bloggingplatformi.dao.MongoCommentDao;
import org.example.bloggingplatformi.dao.MongoReviewDao;
import org.example.bloggingplatformi.dao.ReviewDao;
import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.MongoProvider;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;

/**
 * The Phase 4 measurements, end to end, against the benchmark database.
 *
 * <p>Run with {@code mvn javafx:run@bench} after building the benchmark data
 * (see {@code docs/02-performance-report.md}, "Reproducing"). Every number in
 * that report comes from a CSV this class writes to {@code docs/benchmarks/};
 * none is typed in by hand.
 *
 * <p>Sections:
 * <ol>
 *   <li><b>Indexes</b> - each query timed with the V2 indexes dropped, then
 *       created, with the {@code EXPLAIN ANALYZE} plan of both kept.</li>
 *   <li><b>Full-text</b> - the search expression the DAO used against the one
 *       the index was built on.</li>
 *   <li><b>Pagination</b> - OFFSET against keyset at increasing depth.</li>
 *   <li><b>Cache</b> - the LRU decorator against the bare DAO, on a skewed
 *       workload.</li>
 *   <li><b>DSA</b> - inverted index, hashing, merge sort and binary search
 *       against their naive alternatives, in memory.</li>
 *   <li><b>SQL vs NoSQL</b> - the same comment and review workloads through
 *       both implementations, on identical data.</li>
 * </ol>
 *
 * <p>Timings are warm (data in the buffer cache, JIT compiled), single client,
 * against servers on localhost. They measure query execution plus one local
 * round trip, not a networked production deployment.
 */
public final class PerformanceSuite {

    private static final long SEED = 20261102L;

    private static final List<String> TITLE_FRAGMENTS =
            List.of("eviction", "bloom", "keyset", "vacuum", "deadlock", "sharding");

    private static final List<String> TITLE_QUERIES =
            List.of("eviction", "bloom filters", "keyset pagination", "deadlock detection");

    /** The expression {@code JdbcPostDao} used until this suite showed it never hit the index. */
    private static final String FTS_OLD_EXPRESSION =
            "to_tsvector('english', p.title || ' ' || p.body)";

    /** Character for character the expression {@code idx_posts_search_tsv} is built on. */
    private static final String FTS_INDEXED_EXPRESSION =
            "(setweight(to_tsvector('english', coalesce(p.title, '')), 'A') || "
          + "setweight(to_tsvector('english', coalesce(p.body, '')), 'B'))";

    private final Path out;
    private final Path plans;
    private final Path indexScript;
    private final Random random = new Random(SEED);
    private final List<String> planRows = new ArrayList<>();

    // Parameter pools, sampled once so every variant sees the same inputs.
    private List<Long> authorIds;
    private List<String> titles;
    private List<Integer> tagIds;
    private List<Long> threadPostIds;
    private List<Long> parentCommentIds;
    private List<Long> subtreeRootIds;
    private List<Long> reviewerIds;
    private List<Long[]> pagesOfIds;
    private List<String> rareTerms;
    private List<String> commentKeywords;

    private PerformanceSuite(Path out, Path indexScript) {
        this.out = out;
        this.plans = out.resolve("plans");
        this.indexScript = indexScript;
    }

    public static void main(String[] args) throws Exception {
        String mode = System.getProperty("bench.mode", "run");
        Path out = Path.of(System.getProperty("bench.out", "docs/benchmarks"));
        Path indexScript = Path.of(System.getProperty("bench.indexScript", "db/postgres/V2__indexes.sql"));
        try {
            switch (mode) {
                case "load-mongo" -> MongoBenchLoader.load();
                case "run" -> new PerformanceSuite(out, indexScript).runAll();
                default -> throw new IllegalArgumentException("bench.mode must be run or load-mongo");
            }
        } finally {
            if (Database.isInitialised()) {
                Database.shutdown();
            }
            if (MongoProvider.isInitialised()) {
                MongoProvider.shutdown();
            }
        }
    }

    private void runAll() throws IOException {
        Files.createDirectories(plans);
        guardAgainstDemoDatabase();
        writeEnvironment();
        sampleParameters();

        Benchmark indexes = new Benchmark();
        Benchmark fullText = new Benchmark();

        section("1-2/6  Indexes and full-text search: BASELINE (V2 indexes dropped)");
        dropIndexes();
        indexScenarios(indexes, "no index");
        fullTextScenarios(fullText, "no index");

        section("1-2/6  Indexes and full-text search: INDEXED (V2 indexes created)");
        createIndexes();
        indexScenarios(indexes, "indexed");
        fullTextScenarios(fullText, "indexed");

        indexes.writeCsv(out.resolve("indexes.csv"));
        fullText.writeCsv(out.resolve("fulltext.csv"));

        section("3/6  Pagination: OFFSET vs keyset");
        pagination();

        section("4/6  Cache: LRU decorator vs bare DAO");
        cache();

        section("5/6  In-memory DSA");
        dsa();

        section("6/6  SQL vs NoSQL: identical comment and review workloads");
        sqlVsNoSql();
        storage();

        List<String> planCsv = new ArrayList<>();
        planCsv.add("scenario,phase,plan_nodes,explain_execution_ms");
        planCsv.addAll(planRows);
        Files.write(out.resolve("plans.csv"), planCsv, StandardCharsets.UTF_8);
        System.out.println("\nWrote results to " + out.toAbsolutePath());
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title);
        System.out.println(BenchmarkResult.displayHeader());
    }

    /** Dropping indexes on the demo database would be a nasty surprise. */
    private void guardAgainstDemoDatabase() {
        String name = scalar("SELECT current_database()");
        if (!name.contains("bench")) {
            throw new IllegalStateException("Refusing to run against '" + name
                    + "': the suite drops and recreates indexes. Point db.postgres.url at the "
                    + "benchmark database (javafx:run@bench does this).");
        }
        String mongo = MongoProvider.database().getName();
        if (!mongo.contains("bench")) {
            throw new IllegalStateException("Refusing to run against MongoDB database '" + mongo + "'.");
        }
    }

    // =================================================================
    // 1. Indexes
    // =================================================================

    private record SqlScenario(String name, String sql, IntFunction<Object[]> params) {
    }

    private List<SqlScenario> sqlScenarios() {
        IntFunction<Object[]> none = i -> new Object[0];
        return List.of(
                new SqlScenario("posts_by_author",
                        "SELECT post_id, title, created_at FROM posts WHERE author_id = ? "
                      + "ORDER BY created_at DESC LIMIT 20",
                        i -> new Object[] {pick(authorIds, i)}),
                new SqlScenario("published_feed",
                        "SELECT post_id, title, published_at FROM posts WHERE status = 'PUBLISHED' "
                      + "ORDER BY published_at DESC LIMIT 20",
                        none),
                new SqlScenario("most_viewed_published",
                        "SELECT post_id, title, view_count FROM posts WHERE status = 'PUBLISHED' "
                      + "ORDER BY view_count DESC LIMIT 20",
                        none),
                new SqlScenario("title_exact_case_insensitive",
                        "SELECT post_id FROM posts WHERE LOWER(title) = LOWER(?)",
                        i -> new Object[] {pick(titles, i).toUpperCase(Locale.ROOT)}),
                new SqlScenario("title_substring_count",
                        "SELECT COUNT(*) FROM posts WHERE title ILIKE ?",
                        i -> new Object[] {"%" + pick(TITLE_FRAGMENTS, i) + "%"}),
                new SqlScenario("posts_by_tag",
                        "SELECT p.post_id, p.title FROM posts p WHERE EXISTS "
                      + "(SELECT 1 FROM post_tags pt WHERE pt.post_id = p.post_id AND pt.tag_id = ?) "
                      + "ORDER BY p.created_at DESC LIMIT 20",
                        i -> new Object[] {pick(tagIds, i)}),
                new SqlScenario("comments_for_post",
                        "SELECT comment_id, body, created_at FROM comments WHERE post_id = ? "
                      + "ORDER BY created_at",
                        i -> new Object[] {pick(threadPostIds, i)}),
                new SqlScenario("replies_to_comment",
                        "SELECT comment_id, body FROM comments WHERE parent_comment_id = ? "
                      + "ORDER BY created_at",
                        i -> new Object[] {pick(parentCommentIds, i)}),
                new SqlScenario("comments_by_author",
                        "SELECT comment_id, post_id, created_at FROM comments WHERE author_id = ? "
                      + "ORDER BY created_at DESC LIMIT 20",
                        i -> new Object[] {pick(authorIds, i)}),
                new SqlScenario("moderation_queue",
                        "SELECT comment_id, post_id, created_at FROM comments WHERE status <> 'VISIBLE' "
                      + "ORDER BY created_at DESC LIMIT 50",
                        none),
                new SqlScenario("review_stats_for_page",
                        "SELECT post_id, COUNT(*), ROUND(AVG(rating)::numeric, 2) FROM reviews "
                      + "WHERE post_id = ANY(?) GROUP BY post_id",
                        i -> new Object[] {pick(pagesOfIds, i)}),
                new SqlScenario("reviews_by_reviewer",
                        "SELECT review_id, post_id, rating FROM reviews WHERE reviewer_id = ? "
                      + "ORDER BY created_at DESC LIMIT 20",
                        i -> new Object[] {pick(reviewerIds, i)}));
    }

    private void indexScenarios(Benchmark bench, String phase) {
        for (SqlScenario scenario : sqlScenarios()) {
            explain(scenario.name(), phase, scenario.sql(), scenario.params().apply(0));
            time(bench, scenario.name(), phase, 3, 21, i -> query(scenario.sql(), scenario.params().apply(i)));
        }
    }

    // =================================================================
    // 2. Full-text search
    // =================================================================

    private void fullTextScenarios(Benchmark bench, String phase) {
        String oldSql = "SELECT COUNT(*) FROM posts p WHERE " + FTS_OLD_EXPRESSION
                      + " @@ plainto_tsquery('english', ?)";
        String newSql = "SELECT COUNT(*) FROM posts p WHERE " + FTS_INDEXED_EXPRESSION
                      + " @@ plainto_tsquery('english', ?)";

        explain("fts_old_dao_expression", phase, oldSql, pick(rareTerms, 0));
        explain("fts_index_expression", phase, newSql, pick(rareTerms, 0));

        // Without a usable index each of these computes a tsvector for every
        // one of 100 000 posts, so they get fewer iterations.
        time(bench, "fts_old_dao_expression", phase, 1, 5, i -> query(oldSql, pick(rareTerms, i)));
        boolean fast = phase.equals("indexed");
        time(bench, "fts_index_expression", phase, fast ? 3 : 1, fast ? 21 : 5,
                i -> query(newSql, pick(rareTerms, i)));

        // The application path: JdbcPostDao.search runs a COUNT and a page
        // query, title ILIKE OR full-text, exactly as the post list does.
        JdbcPostDao posts = new JdbcPostDao();
        time(bench, "dao_search_keyword", phase, fast ? 3 : 1, fast ? 21 : 5,
                i -> posts.search(PostFilter.all().withKeyword(pick(rareTerms, i))).totalElements());
    }

    // =================================================================
    // 3. Pagination
    // =================================================================

    private void pagination() throws IOException {
        Benchmark bench = new Benchmark();
        String offsetSql = "SELECT post_id, title, created_at FROM posts "
                + "ORDER BY created_at DESC, post_id DESC LIMIT 20 OFFSET ?";
        String keysetSql = "SELECT post_id, title, created_at FROM posts "
                + "WHERE (created_at, post_id) < (?, ?) "
                + "ORDER BY created_at DESC, post_id DESC LIMIT 20";

        for (int depth : new int[] {0, 1_000, 10_000, 50_000, 99_000}) {
            String label = "page_at_row_" + depth;
            time(bench, label, "OFFSET", 3, 21, i -> query(offsetSql, depth));

            if (depth == 0) {
                // The first page has no cursor; keyset and OFFSET 0 are the same query.
                time(bench, label, "keyset", 3, 21, i -> query(offsetSql, 0));
                continue;
            }
            Object[] cursor = cursorAt(depth - 1);
            List<Long> viaOffset = ids(offsetSql, depth);
            List<Long> viaKeyset = ids(keysetSql, cursor);
            if (!viaOffset.equals(viaKeyset)) {
                throw new IllegalStateException("keyset page differs from OFFSET page at " + depth);
            }
            time(bench, label, "keyset", 3, 21, i -> query(keysetSql, cursor));

            if (depth == 50_000) {
                explain("pagination_offset_50000", "indexed", offsetSql, depth);
                explain("pagination_keyset_50000", "indexed", keysetSql, cursor);
            }
        }
        bench.writeCsv(out.resolve("pagination.csv"));
    }

    private Object[] cursorAt(int offset) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT created_at, post_id FROM posts ORDER BY created_at DESC, post_id DESC "
                  + "OFFSET ? LIMIT 1")) {
                ps.setInt(1, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new Object[] {rs.getObject(1, OffsetDateTime.class), rs.getLong(2)};
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // =================================================================
    // 4. Cache
    // =================================================================

    private void cache() throws IOException {
        Benchmark bench = new Benchmark();
        JdbcPostDao jdbc = new JdbcPostDao();
        CachedPostDao cached = new CachedPostDao(jdbc, 200);

        // A skewed workload, as real traffic is: 80% of reads go to a hot set
        // of 100 posts, the rest are spread over all 100 000.
        long[] hot = random.longs(100, 1, 100_001).toArray();
        long[] workload = new long[2_000];
        for (int i = 0; i < workload.length; i++) {
            workload[i] = random.nextDouble() < 0.8
                    ? hot[random.nextInt(hot.length)]
                    : 1 + random.nextInt(100_000);
        }

        for (long id : hot) {
            if (!jdbc.findById(id).equals(cached.findById(id))) {
                throw new IllegalStateException("cache returned a different post for " + id);
            }
        }
        cached.invalidateAll();

        time(bench, "findById_x2000_skewed", "uncached", 1, 9, i -> lookups(jdbc::findById, workload));
        cached.invalidateAll();
        cached.stats().resetStats();
        time(bench, "findById_x2000_skewed", "LRU cache 200", 1, 9, i -> lookups(cached::findById, workload));
        double hitRate = cached.stats().hitRate();
        long hits = cached.stats().hitCount();
        long misses = cached.stats().missCount();
        long evictions = cached.stats().evictionCount();

        long hotId = hot[0];
        time(bench, "findById_single_hot", "uncached", 20, 201, i -> jdbc.findById(hotId));
        time(bench, "findById_single_hot", "LRU cache 200", 20, 201, i -> cached.findById(hotId));

        bench.writeCsv(out.resolve("cache.csv"));
        Files.write(out.resolve("cache-stats.csv"), List.of(
                "cache,max_entries,workload,hits,misses,evictions,hit_rate",
                String.format(Locale.ENGLISH, "LRU,200,findById_x2000_skewed (80%% to 100 hot ids),%d,%d,%d,%.4f",
                        hits, misses, evictions, hitRate)), StandardCharsets.UTF_8);
        System.out.printf(Locale.ENGLISH, "  cache hit rate over the measured runs: %.1f%%%n", hitRate * 100);
    }

    private static long lookups(java.util.function.LongFunction<java.util.Optional<Post>> finder, long[] ids) {
        long sum = 0;
        for (long id : ids) {
            sum += finder.apply(id).map(Post::viewCount).orElse(0);
        }
        return sum;
    }

    // =================================================================
    // 5. In-memory DSA
    // =================================================================

    private record Row(long id, String title, String lowerTitle, String slug, int views) {
    }

    private void dsa() throws IOException {
        Benchmark bench = new Benchmark();
        List<Row> rows = Database.withConnection(connection -> {
            List<Row> loaded = new ArrayList<>(100_000);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT post_id, title, slug, view_count FROM posts ORDER BY post_id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String title = rs.getString(2);
                    loaded.add(new Row(rs.getLong(1), title, title.toLowerCase(Locale.ROOT),
                            rs.getString(3), rs.getInt(4)));
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return loaded;
        });

        // --- inverted index vs linear scan
        time(bench, "build_inverted_index_100k_titles", "InvertedIndex", 1, 5, i -> buildIndex(rows).postingCount());
        InvertedIndex index = buildIndex(rows);
        for (String query : TITLE_QUERIES) {
            long viaIndex = index.search(query).size();
            long viaScan = scanCount(rows, query);
            if (viaIndex != viaScan) {
                throw new IllegalStateException("index and scan disagree on '" + query + "'");
            }
        }
        time(bench, "title_keyword_search_100k", "inverted index", 20, 201,
                i -> index.search(pick(TITLE_QUERIES, i)).size());
        time(bench, "title_keyword_search_100k", "linear scan", 3, 21,
                i -> scanCount(rows, pick(TITLE_QUERIES, i)));

        // --- hash lookup vs linear scan
        Map<String, Row> bySlug = new HashMap<>(rows.size() * 2);
        rows.forEach(row -> bySlug.put(row.slug(), row));
        List<String> slugKeys = random.ints(100, 0, rows.size()).mapToObj(i -> rows.get(i).slug()).toList();
        time(bench, "slug_lookup_x100", "HashMap", 20, 201, i -> {
            long sum = 0;
            for (String slug : slugKeys) {
                sum += bySlug.get(slug).id();
            }
            return sum;
        });
        time(bench, "slug_lookup_x100", "linear scan", 2, 11, i -> {
            long sum = 0;
            for (String slug : slugKeys) {
                for (Row row : rows) {
                    if (row.slug().equals(slug)) {
                        sum += row.id();
                        break;
                    }
                }
            }
            return sum;
        });

        // --- sorting
        Comparator<Row> byViews = Comparator.comparingInt(Row::views).reversed()
                .thenComparingLong(Row::id);
        List<Row> shuffled = new ArrayList<>(rows);
        java.util.Collections.shuffle(shuffled, new Random(SEED));
        List<Row> merged = Sorting.mergeSort(shuffled, byViews);
        List<Row> library = new ArrayList<>(shuffled);
        library.sort(byViews);
        if (!merged.equals(library)) {
            throw new IllegalStateException("merge sort disagrees with List.sort");
        }
        time(bench, "sort_100k_by_views", "Sorting.mergeSort", 3, 15, i -> Sorting.mergeSort(shuffled, byViews));
        time(bench, "sort_100k_by_views", "List.sort (TimSort)", 3, 15, i -> {
            List<Row> copy = new ArrayList<>(shuffled);
            copy.sort(byViews);
            return copy;
        });

        // --- binary vs linear search over sorted ids
        List<Long> sortedIds = rows.stream().map(Row::id).toList();
        List<Long> keys = random.longs(1_000, 1, 100_001).boxed().toList();
        time(bench, "id_search_x1000_sorted_100k", "Sorting.binarySearch", 20, 201, i -> {
            long found = 0;
            for (Long key : keys) {
                found += Sorting.binarySearch(sortedIds, key, Comparator.naturalOrder()) >= 0 ? 1 : 0;
            }
            return found;
        });
        time(bench, "id_search_x1000_sorted_100k", "Sorting.linearSearch", 2, 11, i -> {
            long found = 0;
            for (Long key : keys) {
                found += Sorting.linearSearch(sortedIds, key, Comparator.naturalOrder()) >= 0 ? 1 : 0;
            }
            return found;
        });

        bench.writeCsv(out.resolve("dsa.csv"));
    }

    private static InvertedIndex buildIndex(List<Row> rows) {
        InvertedIndex index = new InvertedIndex();
        for (Row row : rows) {
            index.add(row.id(), row.title());
        }
        return index;
    }

    /** Whole-word, case-insensitive AND match: the same semantics as the index. */
    private static long scanCount(List<Row> rows, String query) {
        List<String> terms = InvertedIndex.tokenize(query);
        long count = 0;
        for (Row row : rows) {
            List<String> words = InvertedIndex.tokenize(row.title());
            if (words.containsAll(terms)) {
                count++;
            }
        }
        return count;
    }

    // =================================================================
    // 6. SQL vs NoSQL
    // =================================================================

    private void sqlVsNoSql() throws IOException {
        Benchmark bench = new Benchmark();
        CommentDao sqlComments = new JdbcCommentDao();
        CommentDao docComments = new MongoCommentDao();
        ReviewDao sqlReviews = new JdbcReviewDao();
        ReviewDao docReviews = new MongoReviewDao();

        Map<Long, String> objectIds = mongoIdsFor(concat(parentCommentIds, subtreeRootIds));

        // Same answers before same timings: a fast wrong answer is not a win.
        for (Long postId : threadPostIds.subList(0, 10)) {
            List<String> sqlBodies = sqlComments.findByPostId(postId, false).stream().map(Comment::body).toList();
            List<String> docBodies = docComments.findByPostId(postId, false).stream().map(Comment::body).toList();
            if (!sqlBodies.equals(docBodies)) {
                throw new IllegalStateException("thread order differs between stores for post " + postId);
            }
        }

        record Store(String name, CommentDao comments, ReviewDao reviews, boolean mongo) {
        }
        List<Store> stores = List.of(
                new Store("PostgreSQL", sqlComments, sqlReviews, false),
                new Store("MongoDB", docComments, docReviews, true));

        for (Store store : stores) {
            IntFunction<String> parent = i -> store.mongo()
                    ? objectIds.get(pick(parentCommentIds, i)) : String.valueOf(pick(parentCommentIds, i));
            IntFunction<String> root = i -> store.mongo()
                    ? objectIds.get(pick(subtreeRootIds, i)) : String.valueOf(pick(subtreeRootIds, i));

            time(bench, "thread_for_post", store.name(), 5, 51,
                    i -> store.comments().findByPostId(pick(threadPostIds, i), false));
            time(bench, "replies_to_comment", store.name(), 5, 51,
                    i -> store.comments().findReplies(parent.apply(i)));
            time(bench, "subtree_3_levels", store.name(), 5, 51,
                    i -> store.comments().findSubtree(root.apply(i)));
            time(bench, "keyword_search_comments", store.name(), 2, 11,
                    i -> store.comments().search(pick(commentKeywords, i), 50));
            time(bench, "comment_counts_for_page_of_20", store.name(), 5, 51,
                    i -> store.comments().countVisibleByPostIds(List.of(pick(pagesOfIds, i))));
            time(bench, "review_stats_for_page_of_20", store.name(), 5, 51,
                    i -> store.reviews().statsByPostIds(List.of(pick(pagesOfIds, i))));
            time(bench, "moderation_queue_50", store.name(), 5, 51,
                    i -> store.comments().findForModeration(50));

            // Writes: insert roots, then delete exactly those, so the dataset
            // is unchanged afterwards.
            List<String> inserted = new ArrayList<>();
            time(bench, "insert_comment", store.name(), 5, 51, i -> {
                String id = store.comments().insert(Comment.newRoot(
                        pick(threadPostIds, i), 2L, "Bench User 2", "Benchmark insert " + i));
                inserted.add(id);
                return id;
            });
            AtomicInteger next = new AtomicInteger();
            time(bench, "delete_comment", store.name(), 5, 51,
                    i -> store.comments().deleteWithReplies(inserted.get(next.getAndIncrement())));
            while (next.get() < inserted.size()) {
                store.comments().deleteWithReplies(inserted.get(next.getAndIncrement()));
            }

            // The denormalized author name: free in SQL (read by join), a
            // multi-document write in MongoDB.
            time(bench, "rename_author", store.name(), 2, 21, i -> {
                long author = pick(authorIds, i % 10);
                String name = ((i / 10) % 2 == 0) ? "Bench User " + author + " (renamed)" : "Bench User " + author;
                return store.comments().renameAuthor(author, name);
            });
            for (int i = 0; i < 10; i++) {
                long author = pick(authorIds, i);
                store.comments().renameAuthor(author, "Bench User " + author);
            }
        }
        bench.writeCsv(out.resolve("sql-vs-nosql.csv"));
    }

    /** Maps relational comment ids to the ObjectIds of their MongoDB copies. */
    private static Map<Long, String> mongoIdsFor(List<Long> sqlIds) {
        Map<Long, String> ids = new HashMap<>();
        for (Document doc : MongoProvider.comments().find(Filters.in("sqlId", sqlIds))) {
            ids.put(((Number) doc.get("sqlId")).longValue(), doc.getObjectId("_id").toHexString());
        }
        if (ids.size() != new java.util.HashSet<>(sqlIds).size()) {
            throw new IllegalStateException("MongoDB is missing copies of some comments; run bench.mode=load-mongo");
        }
        return ids;
    }

    private void storage() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("store,collection,data_bytes,index_bytes,total_bytes,index_count");
        for (String table : List.of("comments", "reviews")) {
            long data = Long.parseLong(scalar("SELECT pg_table_size('" + table + "')"));
            long index = Long.parseLong(scalar("SELECT pg_indexes_size('" + table + "')"));
            long count = Long.parseLong(scalar("SELECT COUNT(*) FROM pg_indexes WHERE tablename = '" + table + "'"));
            lines.add("PostgreSQL," + table + "," + data + "," + index + "," + (data + index) + "," + count);
        }
        for (String collection : List.of(MongoProvider.COMMENTS, MongoProvider.REVIEWS)) {
            Document stats = MongoProvider.collection(collection)
                    .aggregate(List.of(new Document("$collStats",
                            new Document("storageStats", new Document()))))
                    .first();
            Document storage = stats.get("storageStats", Document.class);
            long data = ((Number) storage.get("storageSize")).longValue();
            long index = ((Number) storage.get("totalIndexSize")).longValue();
            long count = ((Number) storage.get("nindexes")).longValue();
            lines.add("MongoDB," + collection + "," + data + "," + index + "," + (data + index) + "," + count);
        }
        Files.write(out.resolve("storage.csv"), lines, StandardCharsets.UTF_8);
        lines.forEach(line -> System.out.println("  " + line));
    }

    // =================================================================
    // Parameters, environment, index control
    // =================================================================

    private void sampleParameters() {
        Database.withConnection(connection -> {
            try (Statement st = connection.createStatement()) {
                // Same seed, same samples, on every run.
                st.execute("SELECT setseed(0.5)");
            }
            authorIds = longs(connection, "SELECT DISTINCT author_id FROM posts ORDER BY author_id LIMIT 4999", 50);
            titles = strings(connection, "SELECT title FROM posts ORDER BY random() LIMIT 50");
            tagIds = longs(connection, "SELECT tag_id FROM tags ORDER BY random() LIMIT 50", 50)
                    .stream().map(Long::intValue).toList();
            threadPostIds = longs(connection,
                    "SELECT post_id FROM comments GROUP BY post_id HAVING COUNT(*) >= 8 ORDER BY random() LIMIT 50", 50);
            parentCommentIds = longs(connection,
                    "SELECT DISTINCT parent_comment_id FROM comments WHERE parent_comment_id IS NOT NULL "
                  + "ORDER BY parent_comment_id LIMIT 5000", 50);
            subtreeRootIds = longs(connection,
                    "SELECT DISTINCT c1.parent_comment_id FROM comments c2 "
                  + "JOIN comments c1 ON c1.comment_id = c2.parent_comment_id "
                  + "WHERE c1.parent_comment_id IS NOT NULL ORDER BY 1 LIMIT 3000", 30);
            reviewerIds = longs(connection, "SELECT DISTINCT reviewer_id FROM reviews ORDER BY 1 LIMIT 4999", 50);
            List<Long> allPosts = longs(connection, "SELECT post_id FROM posts ORDER BY random() LIMIT 400", 400);
            pagesOfIds = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                pagesOfIds.add(allPosts.subList(i * 20, i * 20 + 20).toArray(new Long[0]));
            }
            return null;
        });
        rareTerms = random.ints(20, 0, 2000).mapToObj(n -> String.format("term%04d", n)).toList();
        commentKeywords = random.ints(20, 0, 5000).mapToObj(n -> String.format("kw%04d", n)).toList();
    }

    /** Takes a random {@code size}-element sample from the rows a query returns. */
    private List<Long> longs(Connection connection, String sql, int size) throws SQLException {
        List<Long> all = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                all.add(rs.getLong(1));
            }
        }
        java.util.Collections.shuffle(all, random);
        return List.copyOf(all.subList(0, Math.min(size, all.size())));
    }

    private static List<String> strings(Connection connection, String sql) throws SQLException {
        List<String> all = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                all.add(rs.getString(1));
            }
        }
        return List.copyOf(all);
    }

    private void writeEnvironment() throws IOException {
        Document build = MongoProvider.database().runCommand(new Document("buildInfo", 1));
        List<String> lines = new ArrayList<>();
        lines.add("Run:        " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        lines.add("Java:       " + Benchmark.environment());
        lines.add("PostgreSQL: " + scalar("SELECT version()"));
        lines.add("            database " + scalar("SELECT current_database()")
                + ", shared_buffers " + scalar("SHOW shared_buffers")
                + ", work_mem " + scalar("SHOW work_mem"));
        lines.add("MongoDB:    " + build.getString("version")
                + ", database " + MongoProvider.database().getName());
        lines.add("Data:       users " + scalar("SELECT COUNT(*) FROM users")
                + ", posts " + scalar("SELECT COUNT(*) FROM posts")
                + ", post_tags " + scalar("SELECT COUNT(*) FROM post_tags")
                + ", comments " + scalar("SELECT COUNT(*) FROM comments")
                + ", reviews " + scalar("SELECT COUNT(*) FROM reviews")
                + " | Mongo comments " + MongoProvider.comments().countDocuments()
                + ", reviews " + MongoProvider.reviews().countDocuments());
        lines.add("Method:     warm buffer cache, JIT warmed, single client, localhost; "
                + "median of N timed iterations after a warm-up (see each CSV)");
        Files.write(out.resolve("environment.txt"), lines, StandardCharsets.UTF_8);
        lines.forEach(System.out::println);
    }

    private record IndexDefinition(String name, String sql) {
    }

    /** The index set, read from V2__indexes.sql so the benchmark cannot drift from it. */
    private List<IndexDefinition> indexDefinitions() {
        try {
            StringBuilder code = new StringBuilder();
            for (String line : Files.readAllLines(indexScript, StandardCharsets.UTF_8)) {
                if (!line.strip().startsWith("--")) {
                    code.append(line).append('\n');
                }
            }
            Matcher matcher = Pattern.compile("CREATE INDEX IF NOT EXISTS (\\w+)[^;]*;", Pattern.DOTALL)
                    .matcher(code);
            List<IndexDefinition> definitions = new ArrayList<>();
            while (matcher.find()) {
                definitions.add(new IndexDefinition(matcher.group(1), matcher.group()));
            }
            if (definitions.isEmpty()) {
                throw new IllegalStateException("no CREATE INDEX statements found in " + indexScript);
            }
            return definitions;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void dropIndexes() {
        indexDefinitions().forEach(index -> execute("DROP INDEX IF EXISTS " + index.name()));
        execute("ANALYZE");
        System.out.println("  dropped " + indexDefinitions().size() + " indexes from " + indexScript);
    }

    private void createIndexes() {
        long start = System.nanoTime();
        indexDefinitions().forEach(index -> execute(index.sql()));
        execute("ANALYZE");
        System.out.printf(Locale.ENGLISH, "  created %d indexes in %.1f s%n",
                indexDefinitions().size(), (System.nanoTime() - start) / 1e9);
    }

    // =================================================================
    // JDBC plumbing
    // =================================================================

    private static void execute(String sql) {
        Database.withConnection(connection -> {
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
            return null;
        });
    }

    private static String scalar(String sql) {
        return Database.withConnection(connection -> {
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getString(1);
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
        });
    }

    /** Runs a query and reads every row, so the cost of fetching is included. */
    private static int query(String sql, Object... params) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(connection, ps, params);
                int rows = 0;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows++;
                    }
                }
                return rows;
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
        });
    }

    private static List<Long> ids(String sql, Object... params) {
        return Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(connection, ps, params);
                List<Long> ids = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                }
                return ids;
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
        });
    }

    private static void bind(Connection connection, PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Long[] array) {
                ps.setArray(i + 1, connection.createArrayOf("bigint", array));
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    /** Saves the plan and records which nodes it used and how long it took. */
    private void explain(String scenario, String phase, String sql, Object... params) {
        List<String> lines = Database.withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS) " + sql)) {
                bind(connection, ps, params);
                List<String> plan = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        plan.add(rs.getString(1));
                    }
                }
                return plan;
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
        });

        List<String> file = new ArrayList<>();
        file.add("-- " + scenario + " (" + phase + ")");
        file.add("-- " + sql);
        file.add("-- parameters: " + describe(params));
        file.addAll(lines);
        try {
            Files.write(plans.resolve(scenario + "--" + phase.replace(' ', '-') + ".txt"), file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> nodes = new ArrayList<>();
        String executionMs = "";
        for (String line : lines) {
            int cost = line.indexOf("(cost=");
            if (cost > 0) {
                String node = line.substring(0, cost).strip();
                if (node.startsWith("->")) {
                    node = node.substring(2).strip();
                }
                nodes.add(node.replaceAll(" on \\S+( \\S+)?$", ""));
            }
            Matcher time = Pattern.compile("Execution Time: ([0-9.]+) ms").matcher(line);
            if (time.find()) {
                executionMs = time.group(1);
            }
        }
        planRows.add(scenario + "," + phase + ",\"" + String.join(" > ", nodes) + "\"," + executionMs);
    }

    private static String describe(Object[] params) {
        List<String> parts = new ArrayList<>();
        for (Object param : params) {
            parts.add(param instanceof Long[] array ? java.util.Arrays.toString(array) : String.valueOf(param));
        }
        return String.join(", ", parts);
    }

    // =================================================================
    // Small helpers
    // =================================================================

    private static BenchmarkResult time(Benchmark bench, String operation, String variant,
                                        int warmup, int iterations, IntFunction<Object> work) {
        AtomicInteger counter = new AtomicInteger();
        BenchmarkResult result = bench.warmup(warmup).iterations(iterations)
                .measure(operation, variant, () -> work.apply(counter.getAndIncrement()));
        System.out.println(result.toDisplayRow());
        return result;
    }

    private static <T> T pick(List<T> values, int i) {
        return values.get(Math.floorMod(i, values.size()));
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> joined = new ArrayList<>(a);
        joined.addAll(b);
        return joined;
    }
}
