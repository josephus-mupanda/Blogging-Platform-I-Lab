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
 * A caching decorator around any {@link PostDao}.
 *
 * <p>Caching lives here rather than in {@code PostService} so that it is a
 * property of data access, not of business logic: the service cannot forget to
 * invalidate, because it never knows a cache exists. Every write method on this
 * class invalidates before it returns, which is the whole correctness argument.
 *
 * <p>Two structures, both O(1):
 * <ul>
 *   <li>an {@link LruCache} keyed by {@code post_id} — the primary store;</li>
 *   <li>a {@link ConcurrentHashMap} from slug to id — a <b>hash index</b> that
 *       turns a slug lookup into a hash probe followed by a cache hit, instead
 *       of a second database round trip.</li>
 * </ul>
 *
 * <h2>What is deliberately not cached</h2>
 * {@link #search} is not. The key would have to be the whole
 * {@link PostFilter} — keyword, author, tag, status, sort, page — and that
 * space is effectively unbounded, so the hit rate would approach zero while the
 * memory cost did not. Worse, a listing must reflect writes immediately;
 * caching it would make a new post appear to vanish. Counts are not cached for
 * the same reason.
 *
 * <p>This is the structure Phase 4 benchmarks: the same workload through this
 * decorator and through the bare DAO, with {@link #stats} reporting the real
 * hit rate rather than an assumption.
 */
public class CachedPostDao implements PostDao {

    private final PostDao delegate;
    private final LruCache<Long, Post> byId;

    /**
     * Hash index: slug to post id.
     *
     * <p>Kept separate from the LRU on purpose. A slug-to-id mapping is
     * immutable and tiny, so evicting it alongside the post body would throw
     * away the cheap half of the lookup. A mapping whose post has been evicted
     * is harmless: the id simply misses in {@code byId} and is loaded.
     */
    private final Map<String, Long> slugIndex = new ConcurrentHashMap<>();

    public CachedPostDao(PostDao delegate, int maxEntries) {
        this.delegate = delegate;
        this.byId = new LruCache<>(maxEntries);
    }

    // -----------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------

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

    /** Not cached: see the class comment. Passes straight through. */
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
        // Not answered from the cache. A cached absence is exactly the case a
        // cache cannot prove: the slug may exist on a row nobody has read yet,
        // and a wrong answer here would let a duplicate slug through.
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

    // -----------------------------------------------------------------
    // Writes - every one of these invalidates
    // -----------------------------------------------------------------

    @Override
    public long insertWithTags(Post post, Set<Integer> tagIds) {
        long id = delegate.insertWithTags(post, tagIds);

        // Not cached on the way in. The returned Post is the one that was
        // built in memory; the row the database actually holds carries
        // defaults this object does not have, such as created_at. Caching the
        // in-memory version would serve a post with null timestamps until it
        // happened to be evicted.
        invalidate(id);
        return id;
    }

    @Override
    public boolean updateWithTags(Post post, Set<Integer> tagIds) {
        boolean changed = delegate.updateWithTags(post, tagIds);
        // Invalidated whether or not a row changed: "no row changed" can mean
        // the row was deleted by someone else, and the stale copy must go
        // either way.
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
     * Increments the counter and keeps the cached copy in step.
     *
     * <p>Invalidating instead would be simpler but self-defeating: reading a
     * post is what increments its view count, so every read of a popular post
     * would evict the entry that caching it was meant to provide. The cached
     * copy is therefore incremented in place. The database remains the
     * authoritative value — its UPDATE is atomic — and the cached number can
     * drift by the few views another client recorded in between, which is an
     * acceptable error for a display counter and is stated in the performance
     * report rather than hidden.
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

    // -----------------------------------------------------------------
    // Cache management
    // -----------------------------------------------------------------

    private void store(Post post) {
        byId.put(post.postId(), post);
        slugIndex.put(post.slug(), post.postId());
    }

    /** Drops one post from both structures. */
    public void invalidate(long postId) {
        byId.get(postId).ifPresent(post -> slugIndex.remove(post.slug()));
        byId.invalidate(postId);

        // The post may have been evicted from the LRU while its slug mapping
        // survived, so the reverse direction is swept as well. The index holds
        // at most a few hundred entries, so this is cheap and keeps a deleted
        // post's slug from resolving to a live id later.
        slugIndex.entrySet().removeIf(entry -> entry.getValue() == postId);
    }

    public void invalidateAll() {
        byId.clear();
        slugIndex.clear();
    }

    /** Hit rate and eviction counts, for the Phase 4 report. */
    public LruCache<Long, Post> stats() {
        return byId;
    }

    public int slugIndexSize() {
        return slugIndex.size();
    }

    /** The DAO underneath, so a benchmark can time the uncached path directly. */
    public PostDao delegate() {
        return delegate;
    }
}
