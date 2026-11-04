package org.example.bloggingplatformi.service;

import java.util.List;
import java.util.Optional;

import org.example.bloggingplatformi.dao.CommentDao;
import org.example.bloggingplatformi.dao.DaoFactory;
import org.example.bloggingplatformi.exception.EntityNotFoundException;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;

/**
 * Business rules for comments. It only talks to the {@link CommentDao}
 * interface, so the same code works with PostgreSQL and with MongoDB.
 * This keeps the speed comparison between the two databases fair.
 */
public class CommentService {

    private static final int MAX_BODY = 10_000;

    private final CommentDao commentDao;

    public CommentService() {
        this(DaoFactory.comments());
    }

    public CommentService(CommentDao commentDao) {
        this.commentDao = commentDao;
    }

    /** Name of the database that stores comments, for the status bar and benchmarks. */
    public String activeStore() {
        return commentDao.storeName();
    }

    public Optional<Comment> findById(String commentId) {
        return commentDao.findById(commentId);
    }

    public Comment require(String commentId) {
        return commentDao.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment", commentId));
    }

    /**
     * Returns a post's comments with each parent before its replies.
     * The UI can show them in this order and indent each one by its depth.
     */
    public List<Comment> threadFor(long postId, boolean includeHidden) {
        return commentDao.findByPostId(postId, includeHidden);
    }

    public List<Comment> search(String keyword, int limit) {
        return commentDao.search(keyword, limit);
    }

    public List<Comment> moderationQueue(int limit) {
        return commentDao.findForModeration(limit);
    }

    public long countFor(long postId, boolean includeHidden) {
        return commentDao.countByPostId(postId, includeHidden);
    }

    public long count() {
        return commentDao.count();
    }

    /** Adds a top-level comment to a post. */
    public Comment addComment(long postId, Long authorId, String authorName, String body) {
        validate(body);
        validatePostId(postId);

        Comment comment = Comment.newRoot(postId, authorId, authorName, body.strip());
        String id = commentDao.insert(comment);
        return comment.withId(id);
    }

    /**
     * Adds a reply. The parent is loaded first because the reply copies its
     * path and depth. MongoDB has no constraint that would catch a wrong path.
     */
    public Comment addReply(String parentId, Long authorId, String authorName, String body) {
        validate(body);
        Comment parent = require(parentId);

        Comment reply = Comment.newReply(parent, authorId, authorName, body.strip());
        String id = commentDao.insert(reply);
        return reply.withId(id);
    }

    public Comment editBody(String commentId, String newBody) {
        validate(newBody);
        if (!commentDao.updateBody(commentId, newBody.strip())) {
            throw new EntityNotFoundException("Comment", commentId);
        }
        return require(commentId);
    }

    public Comment moderate(String commentId, CommentStatus status) {
        if (status == null) {
            throw new ValidationException("status", "A status is required.");
        }
        if (!commentDao.updateStatus(commentId, status)) {
            throw new EntityNotFoundException("Comment", commentId);
        }
        return require(commentId);
    }

    public Comment hide(String commentId) {
        return moderate(commentId, CommentStatus.HIDDEN);
    }

    public Comment restore(String commentId) {
        return moderate(commentId, CommentStatus.VISIBLE);
    }

    /** Deletes a comment and every reply beneath it. */
    public long delete(String commentId) {
        return commentDao.deleteWithReplies(commentId);
    }

    /**
     * Removes every comment on a post. This is needed for MongoDB, because a
     * PostgreSQL cascade cannot reach another database. In PostgreSQL it finds nothing to delete.
     */
    public long deleteForPost(long postId) {
        return commentDao.deleteByPostId(postId);
    }

    private void validate(String body) {
        ValidationException.Collector errors = new ValidationException.Collector();
        if (body == null || body.isBlank()) {
            errors.reject("body", "A comment cannot be empty.");
        } else {
            errors.rejectIf(body.strip().length() > MAX_BODY,
                    "body", "Comments must be at most " + MAX_BODY + " characters.");
        }
        errors.throwIfInvalid();
    }

    private void validatePostId(long postId) {
        if (postId <= 0) {
            throw new ValidationException("postId", "A valid post is required.");
        }
    }
}
