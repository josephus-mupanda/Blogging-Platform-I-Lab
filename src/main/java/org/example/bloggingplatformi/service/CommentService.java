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
 * Business rules for comments, written entirely against {@link CommentDao} so
 * it behaves identically whether the active store is PostgreSQL or MongoDB.
 *
 * <p>That is not an abstraction for its own sake: it is the control that makes
 * the Phase 4 measurement fair. The same service code drives both engines, so
 * any timing difference comes from the store rather than from two different
 * implementations of the surrounding logic.
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

    /** Which engine is active, for the status bar and benchmark labels. */
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
     * One post's thread, parents before their replies. The list is already in
     * display order, so the UI indents on {@link Comment#depth()} without
     * building a tree.
     */
    public List<Comment> threadFor(long postId, boolean includeHidden) {
        return commentDao.findByPostId(postId, includeHidden);
    }

    public List<Comment> repliesTo(String commentId) {
        return commentDao.findReplies(commentId);
    }

    public List<Comment> subtreeOf(String commentId) {
        return commentDao.findSubtree(commentId);
    }

    public List<Comment> byAuthor(long authorId, int limit) {
        return commentDao.findByAuthor(authorId, limit);
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
     * Adds a reply.
     *
     * <p>The parent is loaded first rather than trusting the caller's id,
     * because the reply inherits the parent's path and depth. A wrong parent
     * would put the comment in the wrong place in the thread, and in MongoDB a
     * wrong path is not something a constraint will catch later.
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

    public Comment flag(String commentId) {
        return moderate(commentId, CommentStatus.FLAGGED);
    }

    public Comment restore(String commentId) {
        return moderate(commentId, CommentStatus.VISIBLE);
    }

    /** Deletes a comment and every reply beneath it. */
    public long delete(String commentId) {
        return commentDao.deleteWithReplies(commentId);
    }

    /**
     * Removes every comment on a post.
     *
     * <p>Required when comments live in MongoDB, because deleting the
     * PostgreSQL post row cannot cascade into another database. Harmless when
     * both live in PostgreSQL, where the rows are already gone by the time
     * this runs and it deletes nothing.
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
