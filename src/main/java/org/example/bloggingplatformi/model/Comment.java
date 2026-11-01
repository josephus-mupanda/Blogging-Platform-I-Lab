package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * A comment, in the one shape both stores can produce.
 *
 * <p>The identifier is a {@code String}, not a {@code long}. PostgreSQL issues
 * a {@code BIGSERIAL} and MongoDB issues an {@code ObjectId}, and the whole
 * point of the dual implementation is that the layers above cannot tell which
 * one they are talking to. A string is the only type both can honestly supply.
 * The JDBC DAO renders its {@code comment_id} as decimal digits; the Mongo DAO
 * uses the 24-character hex form.
 *
 * <p>{@code path} and {@code depth} come from the MongoDB materialized-path
 * design. The JDBC implementation derives both in its recursive CTE, so a
 * thread loaded from either store nests identically in the UI.
 *
 * <p>{@code reactions} has no relational column. It is the open-ended field
 * that justifies the document store, and the JDBC DAO returns it empty.
 */
public record Comment(
        String commentId,
        long postId,
        Long authorId,
        String authorName,
        String parentId,
        String path,
        int depth,
        String body,
        CommentStatus status,
        Map<String, Integer> reactions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Path separator and root value, matching the Mongo seed data. */
    public static final String ROOT_PATH = "/";

    public Comment {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(status, "status");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
        reactions = (reactions == null) ? Map.of() : Map.copyOf(reactions);
        if (path == null || path.isBlank()) {
            path = ROOT_PATH;
        }
    }

    /** A new top-level comment, not yet persisted. */
    public static Comment newRoot(long postId, Long authorId, String authorName, String body) {
        return new Comment(null, postId, authorId, authorName, null, ROOT_PATH, 0,
                body, CommentStatus.VISIBLE, Map.of(), null, null);
    }

    /**
     * A new reply. The path and depth are taken from the parent, which is how
     * a subtree stays reachable by a single indexed prefix match.
     */
    public static Comment newReply(Comment parent, Long authorId, String authorName, String body) {
        Objects.requireNonNull(parent, "parent");
        if (parent.commentId == null) {
            throw new IllegalArgumentException("cannot reply to an unsaved comment");
        }
        return new Comment(null, parent.postId, authorId, authorName, parent.commentId,
                parent.path + parent.commentId + "/", parent.depth + 1,
                body, CommentStatus.VISIBLE, Map.of(), null, null);
    }

    public boolean isPersisted() {
        return commentId != null && !commentId.isBlank();
    }

    public boolean isRoot() {
        return parentId == null;
    }

    /** True when the author account was deleted; the thread survives, the name does not. */
    public boolean isOrphaned() {
        return authorId == null;
    }

    public String displayName() {
        return isOrphaned() ? "[deleted user]" : authorName;
    }

    public Comment withId(String id) {
        return new Comment(id, postId, authorId, authorName, parentId, path, depth,
                body, status, reactions, createdAt, updatedAt);
    }

    public Comment withBody(String newBody) {
        return new Comment(commentId, postId, authorId, authorName, parentId, path, depth,
                newBody, status, reactions, createdAt, updatedAt);
    }

    public Comment withStatus(CommentStatus newStatus) {
        return new Comment(commentId, postId, authorId, authorName, parentId, path, depth,
                body, newStatus, reactions, createdAt, updatedAt);
    }
}
