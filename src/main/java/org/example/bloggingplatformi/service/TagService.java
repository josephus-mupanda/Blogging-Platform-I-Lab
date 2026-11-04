package org.example.bloggingplatformi.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.example.bloggingplatformi.dao.TagDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.util.Sorting;

/** Business rules for tags. Uses our own merge sort and binary search. */
public class TagService {

    private static final Comparator<Tag> BY_NAME = Comparator.comparing(Tag::name);

    private final TagDao tagDao = new TagDao();

    /** All tags, sorted by name with our merge sort. */
    public List<Tag> findAll() {
        return Sorting.mergeSort(tagDao.findAll(), BY_NAME);
    }

    /** Tag names are saved in lower case, so "Java" and "java" are the same tag. */
    public void create(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Please type a tag name.");
        }
        String clean = name.strip().toLowerCase();

        // The list is sorted, so binary search can tell quickly if the name is already used.
        if (Sorting.binarySearch(findAll(), new Tag(0, clean), BY_NAME) >= 0) {
            throw new ValidationException("The tag \"" + clean + "\" already exists.");
        }
        tagDao.findOrCreate(clean);
    }

    public void delete(long tagId) {
        tagDao.delete(tagId);
    }

    /** Tag name -> number of posts that use it, most used first. */
    public Map<String, Integer> usage() {
        return tagDao.usage();
    }

    public int count() {
        return tagDao.findAll().size();
    }
}
