package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * A comment on a post, in a form that both PostgreSQL and MongoDB can fill.
 * The id is a String because PostgreSQL gives a number and MongoDB gives an ObjectId.
 * Only MongoDB stores reactions, so they are empty when read from PostgreSQL.
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

    /** The path of a top-level comment. It is also the separator between ids in a path. */
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

    /** A new top-level comment, not yet saved. */
    public static Comment newRoot(long postId, Long authorId, String authorName, String body) {
        return new Comment(null, postId, authorId, authorName, null, ROOT_PATH, 0,
                body, CommentStatus.VISIBLE, Map.of(), null, null);
    }

    /**
     * A new reply. Its path is the parent's path plus the parent's id,
     * so all replies under a comment can be found by searching for that path prefix.
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

    /** True when the author's account was deleted. The comment stays, but without a name. */
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
