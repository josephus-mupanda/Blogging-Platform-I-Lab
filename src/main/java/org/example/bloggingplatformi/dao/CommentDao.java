package org.example.bloggingplatformi.dao;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;

/**
 * Data access for comments, implemented twice: once over PostgreSQL
 * ({@link JdbcCommentDao}) and once over MongoDB ({@link MongoCommentDao}).
 *
 * <p>This interface is the centre of the Epic 4.2 comparison. Because both
 * stores sit behind the same contract, Phase 4 can run identical workloads
 * against each and report real timings instead of an argument about which
 * ought to be faster. The runtime choice comes from {@code app.comments.store}.
 *
 * <p>Two consequences of that symmetry are visible in the signatures:
 * identifiers are {@code String} (a {@code BIGSERIAL} on one side, an
 * {@code ObjectId} on the other), and no method accepts a JDBC
 * {@code Connection}, since one implementation has no such concept.
 */
public interface CommentDao {

    /** Identifies the backing store, for benchmark labels and the UI status bar. */
    String storeName();

    Optional<Comment> findById(String commentId);

    /**
     * One post's whole thread, ordered so that a parent always precedes its
     * replies. The caller can render it by indenting on {@link Comment#depth()}
     * without building a tree first.
     */
    List<Comment> findByPostId(long postId, boolean includeHidden);

    /** Direct replies to one comment. */
    List<Comment> findReplies(String parentId);

    /**
     * The comment and everything beneath it. Backed by a prefix match on the
     * materialized path in MongoDB, and by a recursive CTE in PostgreSQL.
     */
    List<Comment> findSubtree(String rootCommentId);

    List<Comment> findByAuthor(long authorId, int limit);

    /** Case-insensitive keyword search across comment bodies. */
    List<Comment> search(String keyword, int limit);

    /** The moderation queue: everything not VISIBLE. */
    List<Comment> findForModeration(int limit);

    long countByPostId(long postId, boolean includeHidden);

    /**
     * Visible-comment counts for a page of posts, in one round trip.
     *
     * <p>Posts with no comments may be absent from the map. The default loops
     * over {@link #countByPostId}; the real stores override it with a single
     * grouped query, so a twenty-row listing costs one query, not twenty.
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
     * Deletes the comment and every reply beneath it.
     *
     * <p>PostgreSQL does this through {@code ON DELETE CASCADE} on the
     * self-referencing foreign key. MongoDB has no cascade, so the
     * implementation deletes by path prefix; the outcome is the same, and the
     * difference in effort is itself a finding for the Phase 4 write-up.
     *
     * @return the number of documents or rows removed
     */
    long deleteWithReplies(String commentId);

    /** Removes every comment on a post. Used when a post is deleted. */
    long deleteByPostId(long postId);

    /**
     * Updates the denormalized author name wherever it appears.
     *
     * <p>Relational storage does not need this: the name lives in {@code users}
     * and is reached by join. The document store copies it to avoid a
     * cross-store lookup per comment, and that copy has to be maintained. The
     * JDBC implementation is a no-op returning 0, which is the honest answer.
     */
    long renameAuthor(long authorId, String newDisplayName);
}
