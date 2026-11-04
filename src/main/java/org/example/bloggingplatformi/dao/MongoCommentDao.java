package org.example.bloggingplatformi.dao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
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
 * CommentDao for MongoDB. Each comment stores a path of its ancestor ids
 * (for example "/id1/id2/"), so a whole subtree can be found with one prefix match.
 * MongoDB has no cascade, so deletes and author renames are done by hand here.
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
        // Sorting by path would put all top-level comments first, since they all
        // have path "/". So we load the comments oldest first and let
        // threadOrder put each reply under its parent.
        // Hidden comments are removed after ordering, so a visible reply
        // to a hidden comment still keeps its place.
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
            Bson descendants = descendantsOf(root);

            List<Comment> result = new ArrayList<>();
            result.add(map(root));
            result.addAll(collect(comments()
                    .find(descendants)
                    .sort(Sorts.ascending(F_CREATED_AT, "_id"))));
            return threadOrder(result);
        });
    }

    @Override
    public List<Comment> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        // Uses the text index idx_comments_body_text. It only matches whole
        // words, so part of a word finds nothing (unlike ILIKE in PostgreSQL).
        return run(() -> collect(comments()
                .find(Filters.text(keyword.strip()))
                .sort(Sorts.descending(F_CREATED_AT))
                .limit(Math.max(1, limit))));
    }

    @Override
    public List<Comment> findForModeration(int limit) {
        return run(() -> collect(comments()
                // Uses $in with the non-visible statuses instead of $ne VISIBLE.
                // Only $in can use the idx_comments_moderation index. $ne scans every comment.
                .find(Filters.in(F_STATUS, NON_VISIBLE))
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
    public Map<Long, Long> countVisibleByPostIds(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        // $match then $group, the MongoDB version of GROUP BY post_id.
        return run(() -> {
            Map<Long, Long> counts = new HashMap<>();
            for (Document row : comments().aggregate(List.of(
                    Aggregates.match(Filters.and(
                            Filters.in(F_POST_ID, postIds),
                            Filters.eq(F_STATUS, CommentStatus.VISIBLE.name()))),
                    Aggregates.group("$" + F_POST_ID, Accumulators.sum("n", 1))))) {
                counts.put(toLong(row.get("_id"), 0L), toLong(row.get("n"), 0L));
            }
            return counts;
        });
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
            // The old text is saved in an editHistory array. PostgreSQL has no such column.
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
            // MongoDB has no cascade, so the replies are deleted here.
            // One deleteMany on the path prefix removes replies at every depth.
            DeleteResult descendants = comments().deleteMany(descendantsOf(root));
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
        // Each comment keeps a copy of the author name, so a rename must
        // update all of that author's comments.
        return run(() -> comments().updateMany(
                Filters.eq(F_AUTHOR_ID, authorId),
                Updates.combine(Updates.set(F_AUTHOR_NAME, newDisplayName),
                                Updates.set(F_UPDATED_AT, new Date())))
                .getModifiedCount());
    }

    /** Every status except VISIBLE. Built from the enum, so a new status is included. */
    private static final List<String> NON_VISIBLE = java.util.Arrays.stream(CommentStatus.values())
            .filter(status -> status != CommentStatus.VISIBLE)
            .map(Enum::name)
            .toList();

    /**
     * Filter for all replies under root: same post, and a path that starts
     * with the root's path plus the root's id.
     * postId is included so the {postId, path} index can be used.
     */
    private static Bson descendantsOf(Document root) {
        String prefix = root.getString(F_PATH) + root.getObjectId("_id").toHexString() + "/";
        return Filters.and(
                Filters.eq(F_POST_ID, root.get(F_POST_ID)),
                Filters.regex(F_PATH, Pattern.compile("^" + escapeRegex(prefix))));
    }

    /**
     * Escapes regex special characters one by one. Pattern.quote is not used
     * because MongoDB cannot use the index with it.
     */
    static String escapeRegex(String literal) {
        StringBuilder escaped = new StringBuilder(literal.length() + 8);
        for (char c : literal.toCharArray()) {
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    /**
     * Takes comments sorted oldest first and puts each reply right after its parent.
     * A comment whose parent is not in the list is treated as top level.
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

        // Uses a stack instead of recursion, so a very deep thread cannot cause a StackOverflowError.
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

        // Ids may be stored as int or long, so they are read as Number.
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
        // MongoDB dates are stored in UTC with no offset.
        return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static ObjectId parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return ObjectId.isValid(id.strip()) ? new ObjectId(id.strip()) : null;
    }

    /** Runs a MongoDB call and wraps any MongoException in a DataAccessException. */
    private static <T> T run(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (MongoException e) {
            throw new DataAccessException("MongoDB comment operation failed: " + e.getMessage(), e);
        }
    }
}
