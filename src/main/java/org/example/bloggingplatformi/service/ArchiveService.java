package org.example.bloggingplatformi.service;

import java.util.List;

import org.example.bloggingplatformi.dao.CommentDao;
import org.example.bloggingplatformi.dao.PostDao;
import org.example.bloggingplatformi.dao.TagDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.ArchivedItem;
import org.example.bloggingplatformi.model.User;

/** Inspection and restoration of archived content by administrators. */
public class ArchiveService {

    private final UserService userService = new UserService();
    private final PostService postService = new PostService();
    private final PostDao postDao = new PostDao();
    private final CommentDao commentDao = new CommentDao();
    private final TagDao tagDao = new TagDao();

    public List<ArchivedItem> list(ArchivedItem.Kind kind, User actor) {
        userService.requireAdmin(actor);
        return switch (kind) {
            case POST -> postDao.archived();
            case COMMENT -> commentDao.archived();
            case TAG -> tagDao.archived();
        };
    }

    public void restore(ArchivedItem item, User actor) {
        userService.requireAdmin(actor);
        boolean restored = switch (item.kind()) {
            case POST -> postService.restore(Long.parseLong(item.id()));
            case TAG -> tagDao.restore(Long.parseLong(item.id()));
            case COMMENT -> {
                if (!postDao.isVisible(item.parentPostId())) {
                    throw new ValidationException("Restore the parent post before restoring this comment.");
                }
                yield commentDao.restore(item.id());
            }
        };
        if (!restored) {
            throw new ValidationException("This item has already been restored or is no longer available.");
        }
    }
}
