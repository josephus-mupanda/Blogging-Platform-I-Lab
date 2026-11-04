package org.example.bloggingplatformi.dao;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.PostSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CachedPostDao, the cache in front of the post DAO.
 * A fake DAO underneath counts its calls: a cache hit means no call reached it,
 * and a stale read means the cache returned old data after a change.
 */
class CachedPostDaoTest {

    private RecordingPostDao underlying;
    private CachedPostDao cached;

    @BeforeEach
    void setUp() {
        underlying = new RecordingPostDao();
        cached = new CachedPostDao(underlying, 3);
    }

    // -----------------------------------------------------------------
    // Hits and misses
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the first read loads, the second is served from the cache")
    void secondReadIsAHit() {
        long id = seed("First Post", "first-post");

        cached.findById(id);
        cached.findById(id);
        cached.findById(id);

        assertEquals(1, underlying.findByIdCalls.get(),
                "only the first read should have reached the database");
        assertEquals(2, cached.stats().hitCount());
    }

    @Test
    @DisplayName("a slug lookup is served from the hash index after the post is cached")
    void slugLookupUsesHashIndex() {
        long id = seed("Indexed Post", "indexed-post");

        cached.findById(id);                       // loads the post and fills both the cache and the slug index
        cached.findBySlug("indexed-post");
        cached.findBySlug("indexed-post");

        assertEquals(0, underlying.findBySlugCalls.get(),
                "the slug should have resolved through the hash index, not the database");
    }

    @Test
    @DisplayName("a slug lookup populates the cache for the id as well")
    void slugLookupPopulatesIdCache() {
        long id = seed("Both Ways", "both-ways");

        cached.findBySlug("both-ways");
        cached.findById(id);

        assertEquals(1, underlying.findBySlugCalls.get());
        assertEquals(0, underlying.findByIdCalls.get(),
                "findById should have hit the entry stored by the slug lookup");
    }

    @Test
    @DisplayName("a post that does not exist is not cached as absent")
    void absenceIsNotCached() {
        // The post could be inserted later, so "not found" is not cached.
        assertTrue(cached.findById(999).isEmpty());
        assertTrue(cached.findById(999).isEmpty());

        assertEquals(2, underlying.findByIdCalls.get());
    }

    @Test
    @DisplayName("the LRU bound is respected and the oldest post is evicted")
    void respectsCapacity() {
        long a = seed("A", "a");
        long b = seed("B", "b");
        long c = seed("C", "c");
        long d = seed("D", "d");

        cached.findById(a);
        cached.findById(b);
        cached.findById(c);
        cached.findById(d);      // capacity is 3, so `a` is evicted

        assertEquals(3, cached.stats().size());

        cached.findById(a);
        assertEquals(5, underlying.findByIdCalls.get(),
                "the evicted post should have been reloaded");
    }

    // -----------------------------------------------------------------
    // Invalidation: after a write, the cache must not return old data
    // -----------------------------------------------------------------

    @Test
    @DisplayName("an update is visible immediately: no stale read")
    void updateInvalidates() {
        long id = seed("Original Title", "original-title");

        assertEquals("Original Title", cached.findById(id).orElseThrow().title());

        Post edited = underlying.store.get(id).toBuilder().title("Edited Title").build();
        cached.updateWithTags(edited, Set.of());

        assertEquals("Edited Title", cached.findById(id).orElseThrow().title(),
                "the cache served a stale title after an update");
    }

    @Test
    @DisplayName("a deleted post disappears from the cache")
    void deleteInvalidates() {
        long id = seed("Doomed", "doomed");
        cached.findById(id);

        cached.deleteById(id);

        assertTrue(cached.findById(id).isEmpty(),
                "a deleted post was still served from the cache");
    }

    @Test
    @DisplayName("a deleted post's slug stops resolving")
    void deleteClearsTheSlugIndex() {
        // The slug index must also forget the post, or a slug lookup would still find it.
        long id = seed("Doomed", "doomed-slug");
        cached.findBySlug("doomed-slug");

        cached.deleteById(id);

        assertTrue(cached.findBySlug("doomed-slug").isEmpty());
        assertEquals(0, cached.slugIndexSize(), "the slug index still holds the deleted post");
    }

    @Test
    @DisplayName("an update invalidates even when no row was changed")
    void updateInvalidatesOnNoOp() {
        // "No row changed" can mean someone else deleted the row,
        // so the cached copy is removed anyway.
        long id = seed("Present", "present");
        cached.findById(id);

        Post ghost = underlying.store.get(id).toBuilder().postId(12345).build();
        cached.updateWithTags(ghost, Set.of());
        cached.updateWithTags(underlying.store.get(id), Set.of());

        assertFalse(cached.stats().containsKey(id));
    }

    @Test
    @DisplayName("an insert does not cache the in-memory object")
    void insertDoesNotCacheStaleObject() {
        // The object passed to insert has no created_at, but the saved row does.
        // So the next read must load the row from the database.
        Post draft = Post.builder()
                .authorId(1).title("Fresh").slug("fresh").body("Body")
                .status(PostStatus.DRAFT)
                .build();

        long id = cached.insertWithTags(draft, Set.of());
        Post read = cached.findById(id).orElseThrow();

        assertEquals(1, underlying.findByIdCalls.get(),
                "the read after an insert should have gone to the database");
        assertTrue(read.createdAt() != null, "the database copy carries a creation timestamp");
    }

    // -----------------------------------------------------------------
    // View counter
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a view increments the cached copy instead of evicting it")
    void viewKeepsTheEntryWarm() {
        // Every read of a post adds a view. If that removed the cache entry,
        // popular posts would never stay cached.
        long id = seed("Popular", "popular");
        int before = cached.findById(id).orElseThrow().viewCount();

        cached.incrementViewCount(id);
        cached.incrementViewCount(id);

        assertEquals(1, underlying.findByIdCalls.get(), "the entry should still be cached");
        assertEquals(before + 2, cached.findById(id).orElseThrow().viewCount());
    }

    @Test
    @DisplayName("the database remains the authoritative view count")
    void databaseIsAuthoritativeForViews() {
        long id = seed("Popular", "popular-2");
        cached.findById(id);
        cached.incrementViewCount(id);

        assertEquals(1, underlying.store.get(id).viewCount(),
                "the increment must reach the database, not only the cached copy");
    }

    // -----------------------------------------------------------------
    // Pass-through
    // -----------------------------------------------------------------

    @Test
    @DisplayName("search is never cached")
    void searchAlwaysHitsTheDatabase() {
        // Filters rarely repeat, and a cached list would hide new posts.
        cached.search(PostFilter.all());
        cached.search(PostFilter.all());

        assertEquals(2, underlying.searchCalls.get());
    }

    @Test
    @DisplayName("existsBySlug is never answered from the cache")
    void existsBySlugAlwaysHitsTheDatabase() {
        // The cache cannot prove a slug is free, and a wrong answer would allow a duplicate slug.
        seed("Taken", "taken");
        cached.findBySlug("taken");

        cached.existsBySlug("taken");

        assertEquals(1, underlying.existsCalls.get());
    }

    @Test
    @DisplayName("invalidateAll empties both structures")
    void invalidateAllClearsEverything() {
        long id = seed("One", "one");
        cached.findById(id);

        cached.invalidateAll();

        assertEquals(0, cached.stats().size());
        assertEquals(0, cached.slugIndexSize());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private long seed(String title, String slug) {
        return underlying.seed(Post.builder()
                .authorId(1)
                .title(title)
                .slug(slug)
                .body(title + " body")
                .status(PostStatus.PUBLISHED)
                .publishedAt(java.time.OffsetDateTime.now())
                .build());
    }

    /** An in-memory PostDao that counts the calls reaching it. */
    private static final class RecordingPostDao implements PostDao {

        private final Map<Long, Post> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        final AtomicInteger findByIdCalls = new AtomicInteger();
        final AtomicInteger findBySlugCalls = new AtomicInteger();
        final AtomicInteger searchCalls = new AtomicInteger();
        final AtomicInteger existsCalls = new AtomicInteger();

        /** Inserts directly, without going through the counted methods. */
        long seed(Post post) {
            long id = sequence.incrementAndGet();
            store.put(id, post.toBuilder()
                    .postId(id)
                    .createdAt(java.time.OffsetDateTime.now())
                    .updatedAt(java.time.OffsetDateTime.now())
                    .build());
            return id;
        }

        @Override
        public Optional<Post> findById(long postId) {
            findByIdCalls.incrementAndGet();
            return Optional.ofNullable(store.get(postId));
        }

        @Override
        public Optional<Post> findBySlug(String slug) {
            findBySlugCalls.incrementAndGet();
            return store.values().stream().filter(p -> p.slug().equals(slug)).findFirst();
        }

        @Override
        public Page<PostSummary> search(PostFilter filter) {
            searchCalls.incrementAndGet();
            return Page.empty(filter.pageNumber(), filter.pageSize());
        }

        @Override
        public boolean existsBySlug(String slug) {
            existsCalls.incrementAndGet();
            return store.values().stream().anyMatch(p -> p.slug().equals(slug));
        }

        @Override
        public long insertWithTags(Post post, Set<Integer> tagIds) {
            return seed(post);
        }

        @Override
        public boolean updateWithTags(Post post, Set<Integer> tagIds) {
            if (!store.containsKey(post.postId())) {
                return false;
            }
            store.put(post.postId(), post);
            return true;
        }

        @Override
        public boolean deleteById(long postId) {
            return store.remove(postId) != null;
        }

        @Override
        public boolean incrementViewCount(long postId) {
            Post post = store.get(postId);
            if (post == null) {
                return false;
            }
            store.put(postId, post.toBuilder().viewCount(post.viewCount() + 1).build());
            return true;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public long countByStatus(String status) {
            return store.values().stream().filter(p -> p.status().name().equals(status)).count();
        }
    }
}
