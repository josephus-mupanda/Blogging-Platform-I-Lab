package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostSummary;

/** Data access for {@code posts} and their tag assignments. */
public interface PostDao {

    /** The full post including its body. Use {@link #search} for listings. */
    Optional<Post> findById(long postId);

    Optional<Post> findBySlug(String slug);

    /**
     * The paginated, filtered, sorted listing behind the post list screen.
     * Returns summaries, which exclude {@code body}.
     */
    Page<PostSummary> search(PostFilter filter);

    List<PostSummary> findByAuthor(long authorId, int limit, int offset);

    boolean existsBySlug(String slug);

    /**
     * Inserts the post and its tag assignments in one transaction, so a post
     * can never end up persisted with half its tags.
     *
     * @return the generated {@code post_id}
     */
    long insertWithTags(Post post, Set<Integer> tagIds);

    /**
     * Updates the post and replaces its tag assignments, in one transaction.
     *
     * @param tagIds the complete new set; pass null to leave assignments alone
     * @return true if the post row was changed
     */
    boolean updateWithTags(Post post, Set<Integer> tagIds);

    /** Deletes the post. Comments, reviews and tag links cascade. */
    boolean deleteById(long postId);

    /**
     * Increments the view counter.
     *
     * <p>Done as {@code SET view_count = view_count + 1} rather than
     * read-then-write, so the read-modify-write never leaves the database and
     * two concurrent views cannot lose an increment.
     */
    boolean incrementViewCount(long postId);

    long count();

    long countByStatus(String status);
}
