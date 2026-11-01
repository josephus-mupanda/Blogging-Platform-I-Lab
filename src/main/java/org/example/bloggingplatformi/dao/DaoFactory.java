package org.example.bloggingplatformi.dao;

import java.util.Locale;

import org.example.bloggingplatformi.config.AppConfig;

/**
 * Supplies DAO instances, and decides which comment and review store backs
 * this run.
 *
 * <p>{@code app.comments.store} chooses between {@code POSTGRES} and
 * {@code MONGO}. Everything above this class is written against the
 * interfaces, so flipping that one property swaps the storage engine for
 * comments and reviews without another line changing. That property is also
 * what lets the Phase 4 harness benchmark both engines in a single run by
 * constructing each implementation directly.
 *
 * <p>The DAOs are stateless, so single shared instances are safe to reuse
 * across threads.
 */
public final class DaoFactory {

    /** Valid values for {@code app.comments.store}. */
    public enum CommentStore {
        POSTGRES,
        MONGO;

        static CommentStore from(String value) {
            if (value == null || value.isBlank()) {
                return MONGO;
            }
            String normalized = value.strip().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "POSTGRES", "POSTGRESQL", "SQL" -> POSTGRES;
                case "MONGO", "MONGODB", "NOSQL" -> MONGO;
                default -> throw new IllegalArgumentException(
                        "app.comments.store must be POSTGRES or MONGO, was: " + value);
            };
        }
    }

    private static final UserDao USER_DAO = new JdbcUserDao();
    private static final TagDao TAG_DAO = new JdbcTagDao();

    /**
     * The post DAO, wrapped in {@link CachedPostDao} unless caching is off.
     *
     * <p>Decorating here rather than caching inside the service means the
     * layers above cannot forget to invalidate: they never learn a cache
     * exists. Setting {@code app.cache.enabled=false} returns the bare DAO,
     * which is how Phase 4 measures the same workload with and without it.
     */
    private static final PostDao POST_DAO = buildPostDao();

    private static PostDao buildPostDao() {
        JdbcPostDao jdbc = new JdbcPostDao();
        AppConfig config = AppConfig.get();

        if (!config.getBoolean("app.cache.enabled", true)) {
            return jdbc;
        }
        return new CachedPostDao(jdbc, config.getInt("app.cache.posts.maxEntries", 200));
    }

    private static volatile CommentStore commentStore;
    private static volatile CommentDao commentDao;
    private static volatile ReviewDao reviewDao;

    private DaoFactory() {
    }

    public static UserDao users() {
        return USER_DAO;
    }

    public static PostDao posts() {
        return POST_DAO;
    }

    public static TagDao tags() {
        return TAG_DAO;
    }

    /**
     * The caching decorator, when one is active.
     *
     * <p>Used by the Phase 4 harness to read the hit rate and to compare
     * against {@link CachedPostDao#delegate()}, the uncached path.
     */
    public static java.util.Optional<CachedPostDao> postCache() {
        return POST_DAO instanceof CachedPostDao cached
                ? java.util.Optional.of(cached)
                : java.util.Optional.empty();
    }

    public static CommentStore commentStore() {
        if (commentStore == null) {
            synchronized (DaoFactory.class) {
                if (commentStore == null) {
                    commentStore = CommentStore.from(
                            AppConfig.get().getString("app.comments.store", "MONGO"));
                }
            }
        }
        return commentStore;
    }

    public static CommentDao comments() {
        CommentDao local = commentDao;
        if (local == null) {
            synchronized (DaoFactory.class) {
                local = commentDao;
                if (local == null) {
                    local = commentsFor(commentStore());
                    commentDao = local;
                }
            }
        }
        return local;
    }

    public static ReviewDao reviews() {
        ReviewDao local = reviewDao;
        if (local == null) {
            synchronized (DaoFactory.class) {
                local = reviewDao;
                if (local == null) {
                    local = reviewsFor(commentStore());
                    reviewDao = local;
                }
            }
        }
        return local;
    }

    /** Builds a specific implementation regardless of configuration, for benchmarks and tests. */
    public static CommentDao commentsFor(CommentStore store) {
        return switch (store) {
            case POSTGRES -> new JdbcCommentDao();
            case MONGO -> new MongoCommentDao();
        };
    }

    public static ReviewDao reviewsFor(CommentStore store) {
        return switch (store) {
            case POSTGRES -> new JdbcReviewDao();
            case MONGO -> new MongoReviewDao();
        };
    }

    /** Overrides the configured store at runtime. Used by tests and the Phase 4 harness. */
    public static synchronized void useCommentStore(CommentStore store) {
        commentStore = store;
        commentDao = commentsFor(store);
        reviewDao = reviewsFor(store);
    }

    /** Drops the cached instances so the next call re-reads the configuration. */
    public static synchronized void reset() {
        commentStore = null;
        commentDao = null;
        reviewDao = null;
    }
}
