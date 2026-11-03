package org.example.bloggingplatformi.dao;

import org.example.bloggingplatformi.config.AppConfig;

/**
 * Creates the DAOs and hands out one shared instance of each.
 * The app.comments.store setting picks POSTGRES or MONGO for comments and reviews.
 * The rest of the app only uses the interfaces, so no other code has to change.
 */
public final class DaoFactory {

    /** Allowed values for app.comments.store. */
    public enum CommentStore {
        POSTGRES,
        MONGO
    }

    private static final UserDao USER_DAO = new JdbcUserDao();
    private static final TagDao TAG_DAO = new JdbcTagDao();

    /**
     * The post DAO, wrapped in CachedPostDao unless app.cache.enabled is false.
     * The services do not know the cache exists, so they cannot forget to clear it.
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

    private static CommentStore commentStore;
    private static CommentDao commentDao;
    private static ReviewDao reviewDao;

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

    /** Reads app.comments.store the first time, for example MONGO. */
    public static synchronized CommentStore commentStore() {
        if (commentStore == null) {
            String value = AppConfig.get().getString("app.comments.store", "MONGO");
            commentStore = CommentStore.valueOf(value.trim().toUpperCase());
        }
        return commentStore;
    }

    public static synchronized CommentDao comments() {
        if (commentDao == null) {
            commentDao = commentsFor(commentStore());
        }
        return commentDao;
    }

    public static synchronized ReviewDao reviews() {
        if (reviewDao == null) {
            reviewDao = reviewsFor(commentStore());
        }
        return reviewDao;
    }

    /** Builds the chosen version, ignoring the config. Used by benchmarks and tests. */
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

    /** Switches the comment and review store while the app runs. Used by tests and benchmarks. */
    public static synchronized void useCommentStore(CommentStore store) {
        commentStore = store;
        commentDao = commentsFor(store);
        reviewDao = reviewsFor(store);
    }

    /** Clears the saved instances so the next call reads the config again. */
    public static synchronized void reset() {
        commentStore = null;
        commentDao = null;
        reviewDao = null;
    }
}
