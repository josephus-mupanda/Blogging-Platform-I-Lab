package org.example.bloggingplatformi.service;

import java.util.List;

import org.example.bloggingplatformi.dao.CommentDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.User;

/** Business rules for comments (stored in MongoDB). */
public class CommentService {

    private final CommentDao commentDao = new CommentDao();

    public List<Comment> forPost(long postId) {
        return commentDao.findByPost(postId);
    }

    public void add(long postId, User author, String body) {
        checkBody(body);
        commentDao.insert(postId, author.id(), author.displayName(), body.strip());
    }

    /** Only the comment's author or the admin may change or delete it. */
    public void edit(Comment comment, User user, String body) {
        checkAllowed(comment, user);
        checkBody(body);
        commentDao.updateBody(comment.id(), body.strip());
    }

    public void delete(Comment comment, User user) {
        checkAllowed(comment, user);
        commentDao.delete(comment.id());
    }

    public long count() {
        return commentDao.count();
    }

    public boolean canChange(Comment comment, User user) {
        return comment.authorId() == user.id() || user.role().isAdmin();
    }

    private void checkAllowed(Comment comment, User user) {
        if (!canChange(comment, user)) {
            throw new ValidationException("You can only change your own comments.");
        }
    }

    private void checkBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ValidationException("A comment cannot be empty.");
        }
    }
}
