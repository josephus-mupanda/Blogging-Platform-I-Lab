package org.example.bloggingplatformi.dao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import org.example.bloggingplatformi.db.MongoProvider;
import org.example.bloggingplatformi.exception.DataAccessException;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;

/**
 * MongoDB implementation of {@link CommentDao}, using the materialized path
 * described in section 7.1 of the design document.
 *
 * <p>Two differences from {@link JdbcCommentDao} are worth reading the code
 * for, because they are the substance of the Phase 4 comparison:
 *
 * <ul>
 *   <li><b>Subtree reads are a prefix match.</b> {@link #findSubtree} is one
 *       indexed query against {@code path}, where the relational version
 *       recurses once per level of nesting.</li>
 *   <li><b>Cascades are manual.</b> There is no {@code ON DELETE CASCADE}, so
 *       {@link #deleteWithReplies} has to delete by path prefix itself, and
 *       {@link #renameAuthor} has to fix every denormalized copy of the name.
 *       The relational store gets both for free.</li>
 * </ul>
 */
public class MongoCommentDao implements CommentDao {

    private static final String F_POST_ID = "postId";
    private static final String F_AUTHOR_ID = "authorId";
    private static final String F_AUTHOR_NAME = "authorName";
    private static final String F_PARENT_ID = "parentId";
    private static final String F_PATH = "path";
    private static final String F_DEPTH = "depth";
    private static final String F_BODY = "body";
    private static final String F_STATUS = "status";
    private static final String F_REACTIONS = "reactions";
    private static final String F_CREATED_AT = "createdAt";
    private static final String F_UPDATED_AT = "updatedAt";

    private MongoCollection<Document> comments() {
        return MongoProvider.comments();
    }

    @Override
    public String storeName() {
        return "MongoDB";
    }

    @Override
    public Optional<Comment> findById(String commentId) {
        ObjectId id = parseId(commentId);
        if (id == null) {
            return Optional.empty();
        }
        return run(() -> Optional.ofNullable(comments().find(Filters.eq("_id", id)).first())
                .map(MongoCommentDao::map));
    }

    @Override
    public List<Comment> findByPostId(long postId, boolean includeHidden) {
        // A stored path holds the ancestors only, so every root shares "/" and
        // sorting by path alone would list all roots before any reply. Instead
        // read the post's comments in chronological order (one query on
        // idx_comments_post_created) and walk the tree depth-first, which is
        // the order the relational recursive CTE produces.
        //
        // Hidden comments are filtered after the walk, not in the query, for
        // the same reason the CTE filters last: a visible reply to a hidden
        // comment must keep its place in the thread rather than vanish.
        return run(() -> {
            List<Comment> thread = threadOrder(collect(comments()
                    .find(Filters.eq(F_POST_ID, postId))
                    .sort(Sorts.ascending(F_CREATED_AT, "_id"))));
            if (!includeHidden) {
                thread.removeIf(c -> c.status() != CommentStatus.VISIBLE);
            }
            return thread;
        });
    }

    @Override
    public List<Comment> findReplies(String parentId) {
        ObjectId id = parseId(parentId);
        if (id == null) {
            return List.of();
        }
        return run(() -> collect(comments()
                .find(Filters.eq(F_PARENT_ID, id))
                .sort(Sorts.ascending(F_CREATED_AT))));
    }

    @Override
    public List<Comment> findSubtree(String rootCommentId) {
        ObjectId id = parseId(rootCommentId);
        if (id == null) {
            return List.of();
        }
        return run(() -> {
            Document root = comments().find(Filters.eq("_id", id)).first();
            if (root == null) {
                return List.of();
            }
            // Everything below the root shares the prefix "<root.path><root id>/".
            // Pattern.quote is essential: a path is user-independent here, but
            // building a regex from stored data without escaping is the same
            // class of mistake as string-concatenating SQL.
            String prefix = root.getString(F_PATH) + id.toHexString() + "/";
            Bson descendants = Filters.regex(F_PATH, Pattern.compile("^" + Pattern.quote(prefix)));

            List<Comment> result = new ArrayList<>();
            result.add(map(root));
            result.addAll(collect(comments()
                    .find(descendants)
                    .sort(Sorts.ascending(F_CREATED_AT, "_id"))));
            return threadOrder(result);
        });
    }

    @Override
    public List<Comment> findByAuthor(long authorId, int limit) {
        return run(() -> collect(comments()
                .find(Filters.eq(F_AUTHOR_ID, authorId))
                .sort(Sorts.descending(F_CREATED_AT))
                .limit(Math.max(1, limit))));
    }

    @Override
    public List<Comment> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        // Uses idx_comments_body_text. Unlike the relational ILIKE scan this is
        // an index lookup, but it matches whole stemmed words only, so a
        // partial-word query returns nothing. Phase 4 reports both facts.
        return run(() -> collect(comments()
                .find(Filters.text(keyword.strip()))
                .sort(Sorts.descending(F_CREATED_AT))
                .limit(Math.max(1, limit))));
    }

    @Override
    public List<Comment> findForModeration(int limit) {
        return run(() -> collect(comments()
                .find(Filters.ne(F_STATUS, CommentStatus.VISIBLE.name()))
                .sort(Sorts.descending(F_CREATED_AT))
                .limit(Math.max(1, limit))));
    }

    @Override
    public long countByPostId(long postId, boolean includeHidden) {
        Bson filter = includeHidden
                ? Filters.eq(F_POST_ID, postId)
                : Filters.and(Filters.eq(F_POST_ID, postId),
                              Filters.eq(F_STATUS, CommentStatus.VISIBLE.name()));
        return run(() -> comments().countDocuments(filter));
    }

    @Override
    public long count() {
        return run(() -> comments().countDocuments());
    }

    @Override
    public String insert(Comment comment) {
        return run(() -> {
            ObjectId id = new ObjectId();
            Date now = new Date();

            Document document = new Document("_id", id)
                    .append(F_POST_ID, comment.postId())
                    .append(F_AUTHOR_ID, comment.authorId())
                    .append(F_AUTHOR_NAME, comment.authorName())
                    .append(F_PARENT_ID, parseId(comment.parentId()))
                    .append(F_PATH, comment.path())
                    .append(F_DEPTH, comment.depth())
                    .append(F_BODY, comment.body())
                    .append(F_STATUS, comment.status().name())
                    .append(F_REACTIONS, new Document(new HashMap<>(comment.reactions())))
                    .append(F_CREATED_AT, now)
                    .append(F_UPDATED_AT, now);

            comments().insertOne(document);
            return id.toHexString();
        });
    }

    @Override
    public boolean updateBody(String commentId, String newBody) {
        ObjectId id = parseId(commentId);
        if (id == null) {
            return false;
        }
        return run(() -> {
            // $push onto editHistory keeps the previous text. There is no
            // relational column for this; it is one of the open-ended fields
            // the document model exists to carry.
            Document existing = comments().find(Filters.eq("_id", id)).first();
            if (existing == null) {
                return false;
            }
            Bson update = Updates.combine(
                    Updates.set(F_BODY, newBody),
                    Updates.set(F_UPDATED_AT, new Date()),
                    Updates.push("editHistory", new Document("body", existing.getString(F_BODY))
                            .append("editedAt", new Date())));
            UpdateResult result = comments().updateOne(Filters.eq("_id", id), update);
            return result.getModifiedCount() > 0;
        });
    }

    @Override
    public boolean updateStatus(String commentId, CommentStatus status) {
        ObjectId id = parseId(commentId);
        if (id == null) {
            return false;
        }
        return run(() -> {
            UpdateResult result = comments().updateOne(
                    Filters.eq("_id", id),
                    Updates.combine(Updates.set(F_STATUS, status.name()),
                                    Updates.set(F_UPDATED_AT, new Date())));
            return result.getModifiedCount() > 0;
        });
    }

    @Override
    public long deleteWithReplies(String commentId) {
        ObjectId id = parseId(commentId);
        if (id == null) {
            return 0;
        }
        return run(() -> {
            Document root = comments().find(Filters.eq("_id", id)).first();
            if (root == null) {
                return 0L;
            }
            String prefix = root.getString(F_PATH) + id.toHexString() + "/";

            // No ON DELETE CASCADE here, so the subtree is removed explicitly.
            // One prefix-matched deleteMany covers every depth at once, which
            // is the advantage the materialized path buys back.
            DeleteResult descendants = comments().deleteMany(
                    Filters.regex(F_PATH, Pattern.compile("^" + Pattern.quote(prefix))));
            DeleteResult self = comments().deleteOne(Filters.eq("_id", id));

            return descendants.getDeletedCount() + self.getDeletedCount();
        });
    }

    @Override
    public long deleteByPostId(long postId) {
        return run(() -> comments().deleteMany(Filters.eq(F_POST_ID, postId)).getDeletedCount());
    }

    @Override
    public long renameAuthor(long authorId, String newDisplayName) {
        // The price of the denormalized authorName: a rename is a write across
        // every comment that person ever left. The relational store reaches the
        // name by join and needs no equivalent.
        return run(() -> comments().updateMany(
                Filters.eq(F_AUTHOR_ID, authorId),
                Updates.combine(Updates.set(F_AUTHOR_NAME, newDisplayName),
                                Updates.set(F_UPDATED_AT, new Date())))
                .getModifiedCount());
    }

    // -----------------------------------------------------------------
    // Mapping and plumbing
    // -----------------------------------------------------------------

    /**
     * Reorders comments, given in chronological order, into thread order: each
     * comment followed by its replies, depth-first, siblings oldest first.
     *
     * <p>A comment whose parent is not in the list is treated as a root, so a
     * partial result (a subtree, or a thread with a missing parent) loses
     * nothing. Runs in O(n).
     */
    static List<Comment> threadOrder(List<Comment> chronological) {
        Set<String> present = new HashSet<>();
        for (Comment c : chronological) {
            present.add(c.commentId());
        }

        List<Comment> roots = new ArrayList<>();
        Map<String, List<Comment>> children = new HashMap<>();
        for (Comment c : chronological) {
            if (c.parentId() == null || !present.contains(c.parentId())) {
                roots.add(c);
            } else {
                children.computeIfAbsent(c.parentId(), k -> new ArrayList<>()).add(c);
            }
        }

        // Iterative, so a pathologically deep thread cannot overflow the stack.
        List<Comment> ordered = new ArrayList<>(chronological.size());
        Deque<Comment> stack = new ArrayDeque<>();
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.push(roots.get(i));
        }
        while (!stack.isEmpty()) {
            Comment c = stack.pop();
            ordered.add(c);
            List<Comment> kids = children.getOrDefault(c.commentId(), List.of());
            for (int i = kids.size() - 1; i >= 0; i--) {
                stack.push(kids.get(i));
            }
        }
        return ordered;
    }

    private static List<Comment> collect(FindIterable<Document> documents) {
        List<Comment> comments = new ArrayList<>();
        for (Document document : documents) {
            comments.add(map(document));
        }
        return comments;
    }

    static Comment map(Document document) {
        ObjectId parentId = document.getObjectId(F_PARENT_ID);

        Map<String, Integer> reactions = new HashMap<>();
        Document stored = document.get(F_REACTIONS, Document.class);
        if (stored != null) {
            stored.forEach((key, value) -> {
                if (value instanceof Number number) {
                    reactions.put(key, number.intValue());
                }
            });
        }

        // The seed writes postId and authorId as NumberLong, but a document
        // inserted elsewhere could hold an int. Reading through Number covers
        // both rather than throwing a ClassCastException on valid data.
        return new Comment(
                document.getObjectId("_id").toHexString(),
                toLong(document.get(F_POST_ID), 0L),
                document.get(F_AUTHOR_ID) == null ? null : toLong(document.get(F_AUTHOR_ID), 0L),
                document.getString(F_AUTHOR_NAME),
                parentId == null ? null : parentId.toHexString(),
                document.getString(F_PATH),
                toInt(document.get(F_DEPTH)),
                document.getString(F_BODY),
                CommentStatus.from(document.getString(F_STATUS)),
                reactions,
                toOffsetDateTime(document.getDate(F_CREATED_AT)),
                toOffsetDateTime(document.getDate(F_UPDATED_AT)));
    }

    private static long toLong(Object value, long fallback) {
        return (value instanceof Number number) ? number.longValue() : fallback;
    }

    private static int toInt(Object value) {
        return (value instanceof Number number) ? number.intValue() : 0;
    }

    private static OffsetDateTime toOffsetDateTime(Date date) {
        // BSON dates are UTC instants with no stored offset, so UTC is the
        // honest reading. The SQL side keeps a real offset in TIMESTAMPTZ.
        return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static ObjectId parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return ObjectId.isValid(id.strip()) ? new ObjectId(id.strip()) : null;
    }

    /** Wraps driver failures in the shared exception type, as the DAO contract requires. */
    private static <T> T run(MongoOperation<T> operation) {
        try {
            return operation.execute();
        } catch (MongoException e) {
            throw new DataAccessException("MongoDB comment operation failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface MongoOperation<T> {
        T execute();
    }
}
