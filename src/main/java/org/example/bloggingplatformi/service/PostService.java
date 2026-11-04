package org.example.bloggingplatformi.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.bloggingplatformi.dao.CommentDao;
import org.example.bloggingplatformi.dao.PostDao;
import org.example.bloggingplatformi.dao.ReviewDao;
import org.example.bloggingplatformi.dao.TagDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;

/**
 * Business rules for posts: validation, the cache, and deleting a post's
 * comments and reviews in MongoDB when the post is deleted.
 */
public class PostService {

    /** How many posts one page of the post list shows. */
    public static final int PAGE_SIZE = 10;

    /**
     * In-memory cache: post id -> post. A HashMap finds a post in O(1),
     * without asking the database. It is static, so the whole app shares one cache.
     */
    private static final Map<Long, Post> CACHE = new HashMap<>();

    private final PostDao postDao = new PostDao();
    private final TagDao tagDao = new TagDao();
    private final CommentDao commentDao = new CommentDao();
    private final ReviewDao reviewDao = new ReviewDao();

    /** Reads the post from the cache if it is there; otherwise from the database, then keeps it in the cache. */
    public Post findById(long postId) {
        Post post = CACHE.get(postId);
        if (post == null) {
            post = postDao.findById(postId);
            if (post != null) {
                CACHE.put(postId, post);
            }
        }
        return post;
    }

    /** One page of posts that match the filters. page starts at 0. */
    public List<Post> search(String keyword, Long authorId, Long tagId, int page) {
        return postDao.search(keyword, authorId, tagId, PAGE_SIZE, page * PAGE_SIZE);
    }

    public int countPages(String keyword, Long authorId, Long tagId) {
        int posts = postDao.count(keyword, authorId, tagId);
        return Math.max(1, (posts + PAGE_SIZE - 1) / PAGE_SIZE); // round up, and at least 1 page
    }

    public List<Post> latest(int howMany) {
        return postDao.search(null, null, null, howMany, 0);
    }

    public int count() {
        return postDao.count(null, null, null);
    }

    public List<Tag> tagsOf(long postId) {
        return tagDao.findByPost(postId);
    }

    /** tagNames is what the user typed, for example "java, SQL". */
    public long create(long authorId, String title, String body, String tagNames) {
        validate(title, body);
        return postDao.insert(authorId, title.strip(), body.strip(), tagIds(tagNames));
    }

    public void update(long postId, String title, String body, String tagNames) {
        validate(title, body);
        postDao.update(postId, title.strip(), body.strip(), tagIds(tagNames));
        CACHE.remove(postId); // cache invalidation: the cached copy is now out of date
    }

    /**
     * Deletes the post, and first its comments and reviews in MongoDB:
     * PostgreSQL's ON DELETE CASCADE cannot reach another database.
     */
    public void delete(long postId) {
        commentDao.deleteByPost(postId);
        reviewDao.deleteByPost(postId);
        postDao.delete(postId);
        CACHE.remove(postId); // cache invalidation
    }

    private void validate(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("A post needs a title.");
        }
        if (title.strip().length() > 200) {
            throw new ValidationException("The title can have at most 200 characters.");
        }
        if (body == null || body.isBlank()) {
            throw new ValidationException("A post needs some text.");
        }
    }

    /** Turns "java, SQL" into the ids of the tags "java" and "sql" (created if they are new). */
    private List<Long> tagIds(String tagNames) {
        List<Long> ids = new ArrayList<>();
        if (tagNames == null) {
            return ids;
        }
        for (String name : tagNames.split(",")) {
            String clean = name.strip().toLowerCase();
            if (!clean.isEmpty()) {
                long id = tagDao.findOrCreate(clean);
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
