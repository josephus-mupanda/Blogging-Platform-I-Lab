package org.example.bloggingplatformi.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.bloggingplatformi.algo.Sorting;
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

    /**
     * All tags, sorted by name with our own merge sort (Sorting.mergeSort)
     * instead of an SQL ORDER BY.
     */
    public List<Tag> findAll() {
        return Sorting.mergeSort(tagDao.findAll(), Comparator.comparing(Tag::name));
    }

    public Optional<Tag> findByName(String name) {
        return tagDao.findByName(name);
    }

    public List<Tag> findForPost(long postId) {
        return tagDao.findByPostId(postId);
    }

    /** Returns tags that start with the typed text, for the tag picker. */
    public List<Tag> suggest(String prefix) {
        return tagDao.searchByPrefix(prefix, AUTOCOMPLETE_LIMIT);
    }

    /** Maps each tag name to its number of posts, most used first. */
    public Map<String, Long> usageCounts(int limit) {
        return tagDao.usageCounts(limit);
    }

    public long count() {
        return tagDao.count();
    }

    /**
     * Returns the id of the tag with this name, creating the tag if it is new.
     * Names are lower-cased, so "Java" and " JAVA " end up as the same tag.
     */
    public int findOrCreate(String name) {
        validateName(name);
        return tagDao.findOrCreate(name);
    }

    public boolean delete(int tagId) {
        // The database removes the tag's post_tags rows. The posts stay.
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
