package org.example.bloggingplatformi.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.bloggingplatformi.dao.PostDao;
import org.example.bloggingplatformi.dao.TagDao;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.model.User;

/** Business rules for posts: validation, the cache, and recoverable deletion. */
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
    private final UserService userService = new UserService();

    /** Reads the post from the cache if it is there; otherwise from the database, then keeps it in the cache. */
    public Post findById(long postId) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(postId, postDao::findById);
        }
    }

    /** Recheck visibility before opening a post, even if another window cached it. */
    public Post open(long postId) {
        synchronized (CACHE) {
            if (CACHE.containsKey(postId) && !postDao.isVisible(postId)) {
                CACHE.remove(postId);
                throw new ValidationException("This post is no longer available.");
            }
            Post post = findById(postId);
            if (post == null) {
                throw new ValidationException("This post is no longer available.");
            }
            return post;
        }
    }

    public void requireVisible(long postId) {
        if (!postDao.isVisible(postId)) {
            throw new ValidationException("This post is no longer available.");
        }
    }

    /** One page of posts that match the filters. page starts at 0. */
    public List<Post> search(String keyword, Long authorId, Long tagId, int page) {
        return postDao.search(keyword, authorId, tagId, PAGE_SIZE, page * PAGE_SIZE);
    }

    public int countMatches(String keyword, Long authorId, Long tagId) {
        return postDao.count(keyword, authorId, tagId);
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

    public long create(long authorId, String title, String body, List<Long> tagIds) {
        validate(title, body);
        synchronized (CACHE) {
            Post saved = postDao.insert(authorId, title.strip(), body.strip(), tagIds);
            CACHE.put(saved.id(), saved);
            return saved.id();
        }
    }

    public void update(long postId, String title, String body, List<Long> tagIds) {
        validate(title, body);
        synchronized (CACHE) {
            Post saved = postDao.update(postId, title.strip(), body.strip(), tagIds);
            CACHE.put(postId, saved);
        }
    }

    public void archive(long postId, User actor) {
        userService.requireActive(actor);
        synchronized (CACHE) {
            CACHE.remove(postId);
            if (!postDao.archive(postId, actor.id(), actor.role().isAdmin())) {
                throw new ValidationException("This post is already in Trash or you cannot remove it.");
            }
        }
    }

    boolean restore(long postId) {
        synchronized (CACHE) {
            CACHE.remove(postId);
            return postDao.restore(postId);
        }
    }

    public List<Long> archivedIds() {
        return postDao.archivedIds();
    }

    private void validate(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("title", "A post needs a title.");
        }
        if (title.strip().length() > 200) {
            throw new ValidationException("title", "Use 200 characters or fewer for the title.");
        }
        if (body == null || body.isBlank()) {
            throw new ValidationException("body", "A post needs some text.");
        }
    }

}
