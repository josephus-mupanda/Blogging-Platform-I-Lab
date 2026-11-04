package org.example.bloggingplatformi.dao;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;

/**
 * Reads and writes comments. There are two versions: JdbcCommentDao (PostgreSQL)
 * and MongoCommentDao (MongoDB), chosen by the app.comments.store setting.
 * Ids are Strings because PostgreSQL uses a number and MongoDB uses an ObjectId.
 */
public interface CommentDao {

    /** Name of the store in use, shown in benchmark output and the status bar. */
    String storeName();

    Optional<Comment> findById(String commentId);

    /**
     * All comments on a post, with each parent before its replies.
     * The UI can indent each one by its depth without building a tree.
     */
    List<Comment> findByPostId(long postId, boolean includeHidden);

    /** Direct replies to one comment. */
    List<Comment> findReplies(String parentId);

    /**
     * The comment and all replies under it. MongoDB matches on the path prefix.
     * PostgreSQL uses a recursive query.
     */
    List<Comment> findSubtree(String rootCommentId);

    /** Searches comment bodies for a keyword, ignoring case. */
    List<Comment> search(String keyword, int limit);

    /** Comments waiting for moderation: everything that is not VISIBLE. */
    List<Comment> findForModeration(int limit);

    long countByPostId(long postId, boolean includeHidden);

    /**
     * Visible comment counts for many posts at once.
     * Posts with no comments may be missing from the map. Both stores override
     * this default loop with one grouped query, so a page of 20 posts needs one query.
     */
    default Map<Long, Long> countVisibleByPostIds(Collection<Long> postIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Long postId : postIds) {
            counts.put(postId, countByPostId(postId, false));
        }
        return counts;
    }

    long count();

    /** @return the identifier assigned by the store */
    String insert(Comment comment);

    boolean updateBody(String commentId, String newBody);

    boolean updateStatus(String commentId, CommentStatus status);

    /**
     * Deletes the comment and all replies under it.
     * PostgreSQL does this with ON DELETE CASCADE. MongoDB has no cascade,
     * so that version deletes by path prefix.
     *
     * @return how many comments were removed
     */
    long deleteWithReplies(String commentId);

    /** Removes every comment on a post. Used when a post is deleted. */
    long deleteByPostId(long postId);

    /**
     * Updates the author name stored on the author's comments.
     * Only MongoDB keeps a copy of the name. The JDBC version does nothing and returns 0.
     */
    long renameAuthor(long authorId, String newDisplayName);
}
