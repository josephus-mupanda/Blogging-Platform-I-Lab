package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.model.Tag;

/** Reads and writes tags, and the post_tags table that links tags to posts. */
public interface TagDao {

    Optional<Tag> findById(int tagId);

    /** Ignores case. Tag names are stored in lower case. */
    Optional<Tag> findByName(String name);

    List<Tag> findAll();

    /** The tags on one post, in alphabetical order. */
    List<Tag> findByPostId(long postId);

    /** Maps each tag name to how many posts use it. */
    Map<String, Long> usageCounts(int limit);

    long insert(Tag tag);

    boolean deleteById(int tagId);

    /**
     * Returns the id of the tag with this name, creating the tag first if needed.
     * This lets the editor accept new tags typed by the user.
     */
    int findOrCreate(String name);

    long count();
}
