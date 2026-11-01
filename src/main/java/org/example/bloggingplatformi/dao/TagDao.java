package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.model.Tag;

/** Data access for {@code tags} and the {@code post_tags} association. */
public interface TagDao {

    Optional<Tag> findById(int tagId);

    /** Case-insensitive; tag names are stored lower-case. */
    Optional<Tag> findByName(String name);

    List<Tag> findAll();

    /** The tags attached to one post, alphabetically. */
    List<Tag> findByPostId(long postId);

    /** Prefix search for the tag picker's autocomplete. */
    List<Tag> searchByPrefix(String prefix, int limit);

    /** Tag name to post count, for the tag cloud and analytics screen. */
    Map<String, Long> usageCounts(int limit);

    long insert(Tag tag);

    boolean deleteById(int tagId);

    /**
     * Finds the tag by name, inserting it if it does not exist yet, and
     * returns its id. This is what lets the editor accept free-text tags.
     */
    int findOrCreate(String name);

    long count();
}
