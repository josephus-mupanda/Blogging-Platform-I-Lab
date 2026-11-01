package org.example.bloggingplatformi.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.dao.DaoFactory;
import org.example.bloggingplatformi.dao.TagDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Tag;

/** Business rules for the tag vocabulary. */
public class TagService {

    private static final int MAX_NAME = 50;
    private static final int AUTOCOMPLETE_LIMIT = 10;

    private final TagDao tagDao;

    public TagService() {
        this(DaoFactory.tags());
    }

    public TagService(TagDao tagDao) {
        this.tagDao = tagDao;
    }

    public List<Tag> findAll() {
        return tagDao.findAll();
    }

    public Optional<Tag> findByName(String name) {
        return tagDao.findByName(name);
    }

    public List<Tag> findForPost(long postId) {
        return tagDao.findByPostId(postId);
    }

    /** Backs the tag picker's type-ahead. */
    public List<Tag> suggest(String prefix) {
        return tagDao.searchByPrefix(prefix, AUTOCOMPLETE_LIMIT);
    }

    /** Tag name to post count, most used first. Feeds the analytics screen. */
    public Map<String, Long> usageCounts(int limit) {
        return tagDao.usageCounts(limit);
    }

    public long count() {
        return tagDao.count();
    }

    /**
     * Resolves a name to an id, creating the tag if it is new.
     *
     * <p>Normalization to lower case happens in {@link Tag} and again in the
     * DAO, which matters: "Java", "java" and " JAVA " must all land on one row,
     * or the vocabulary fragments and the tag filter stops being trustworthy.
     */
    public int findOrCreate(String name) {
        validateName(name);
        return tagDao.findOrCreate(name);
    }

    public boolean delete(int tagId) {
        // post_tags rows go with it through ON DELETE CASCADE; the posts
        // themselves are untouched.
        return tagDao.deleteById(tagId);
    }

    private void validateName(String name) {
        ValidationException.Collector errors = new ValidationException.Collector();
        if (name == null || name.isBlank()) {
            errors.reject("name", "A tag name is required.");
        } else {
            errors.rejectIf(name.strip().length() > MAX_NAME,
                    "name", "Tag names must be at most " + MAX_NAME + " characters.");
        }
        errors.throwIfInvalid();
    }
}
