package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostSummary;

/** Reads and writes posts and the tags linked to each post. */
public interface PostDao {

    /** Loads the full post, including the body. Use search() for lists. */
    Optional<Post> findById(long postId);

    Optional<Post> findBySlug(String slug);

    /**
     * One page of posts for the post list screen, filtered and sorted.
     * Returns summaries, which leave out the body.
     */
    Page<PostSummary> search(PostFilter filter);

    List<PostSummary> findByAuthor(long authorId, int limit, int offset);

    boolean existsBySlug(String slug);

    /**
     * Inserts the post and its tags in one transaction, so a post is never
     * saved with only some of its tags.
     *
     * @return the new post_id
     */
    long insertWithTags(Post post, Set<Integer> tagIds);

    /**
     * Updates the post and replaces its tags, in one transaction.
     *
     * @param tagIds the full new set of tags, or null to keep the current tags
     * @return true if the post row was changed
     */
    boolean updateWithTags(Post post, Set<Integer> tagIds);

    /** Deletes the post. Its comments, reviews and tag links are deleted by cascade. */
    boolean deleteById(long postId);

    /**
     * Adds one to the view count. The database does the addition, so two
     * views at the same time cannot overwrite each other.
     */
    boolean incrementViewCount(long postId);

    long count();

    long countByStatus(String status);
}
