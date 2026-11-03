package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.example.bloggingplatformi.cache.LruCache;
import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostSummary;

/**
 * Wraps a PostDao and keeps recently read posts in memory.
 * Posts are cached by id in an LRU cache, and a map from slug to id lets slug lookups use it too.
 * Every write clears the affected post. Search results and counts are not cached.
 */
public class CachedPostDao implements PostDao {

    private final PostDao delegate;
    private final LruCache<Long, Post> byId;

    /**
     * Maps slug to post id. If the post itself was evicted from the LRU,
     * the id just misses in byId and the post is loaded again.
     */
    private final Map<String, Long> slugIndex = new ConcurrentHashMap<>();

    public CachedPostDao(PostDao delegate, int maxEntries) {
        this.delegate = delegate;
        this.byId = new LruCache<>(maxEntries);
    }

    @Override
    public Optional<Post> findById(long postId) {
        Optional<Post> cached = byId.get(postId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Post> loaded = delegate.findById(postId);
        loaded.ifPresent(this::store);
        return loaded;
    }

    @Override
    public Optional<Post> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String key = slug.strip();

        Long id = slugIndex.get(key);
        if (id != null) {
            Optional<Post> cached = byId.get(id);
            if (cached.isPresent()) {
                return cached;
            }
        }
        Optional<Post> loaded = delegate.findBySlug(key);
        loaded.ifPresent(this::store);
        return loaded;
    }

    /** Not cached. Filter combinations are endless and lists must show new posts at once. */
    @Override
    public Page<PostSummary> search(PostFilter filter) {
        return delegate.search(filter);
    }

    @Override
    public List<PostSummary> findByAuthor(long authorId, int limit, int offset) {
        return delegate.findByAuthor(authorId, limit, offset);
    }

    @Override
    public boolean existsBySlug(String slug) {
        // Always ask the database. The cache only holds posts that were read,
        // so a missing slug in the cache does not mean it is free.
        return delegate.existsBySlug(slug);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public long countByStatus(String status) {
        return delegate.countByStatus(status);
    }

    @Override
    public long insertWithTags(Post post, Set<Integer> tagIds) {
        long id = delegate.insertWithTags(post, tagIds);

        // Do not cache the new post here. This object lacks values the database
        // fills in, such as created_at, so it is loaded fresh on the next read.
        invalidate(id);
        return id;
    }

    @Override
    public boolean updateWithTags(Post post, Set<Integer> tagIds) {
        boolean changed = delegate.updateWithTags(post, tagIds);
        // Clear it even if nothing changed. The row may have been deleted elsewhere.
        invalidate(post.postId());
        return changed;
    }

    @Override
    public boolean deleteById(long postId) {
        boolean deleted = delegate.deleteById(postId);
        invalidate(postId);
        return deleted;
    }

    /**
     * Adds one view in the database and in the cached copy.
     * Clearing the cache here would empty it on every read, since each read adds a view.
     * The cached count can be a little behind the database, which is fine for display.
     */
    @Override
    public boolean incrementViewCount(long postId) {
        boolean updated = delegate.incrementViewCount(postId);
        if (updated) {
            byId.get(postId).ifPresent(cached ->
                    byId.put(postId, cached.toBuilder()
                            .viewCount(cached.viewCount() + 1)
                            .build()));
        }
        return updated;
    }

    private void store(Post post) {
        byId.put(post.postId(), post);
        slugIndex.put(post.slug(), post.postId());
    }

    /** Removes one post from the cache and the slug map. */
    public void invalidate(long postId) {
        byId.get(postId).ifPresent(post -> slugIndex.remove(post.slug()));
        byId.invalidate(postId);

        // The post may already be gone from the LRU while its slug is still in
        // the map, so also remove any slug that points to this id.
        slugIndex.entrySet().removeIf(entry -> entry.getValue() == postId);
    }

    public void invalidateAll() {
        byId.clear();
        slugIndex.clear();
    }

    /** The LRU cache, which holds the hit and eviction counts. */
    public LruCache<Long, Post> stats() {
        return byId;
    }

    public int slugIndexSize() {
        return slugIndex.size();
    }

    /** The real DAO underneath, so benchmarks can time it without the cache. */
    public PostDao delegate() {
        return delegate;
    }
}
