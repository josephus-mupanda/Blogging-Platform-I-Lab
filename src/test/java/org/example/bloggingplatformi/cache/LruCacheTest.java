package org.example.bloggingplatformi.cache;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LruCacheTest {

    @Test
    @DisplayName("stores and returns a value")
    void storesAndReturns() {
        LruCache<String, String> cache = new LruCache<>(10);
        cache.put("a", "alpha");

        assertEquals("alpha", cache.get("a").orElseThrow());
    }

    @Test
    @DisplayName("a missing key returns empty rather than null")
    void missReturnsEmpty() {
        assertTrue(new LruCache<String, String>(10).get("nope").isEmpty());
    }

    @Test
    @DisplayName("evicts the least recently used entry when over capacity")
    void evictsLeastRecentlyUsed() {
        LruCache<Integer, String> cache = new LruCache<>(3);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        cache.put(4, "four");

        assertEquals(3, cache.size());
        assertFalse(cache.containsKey(1), "key 1 was the oldest and should have been evicted");
        assertTrue(cache.containsKey(4));
        assertEquals(1, cache.evictionCount());
    }

    @Test
    @DisplayName("reading an entry promotes it, so it survives the next eviction")
    void readPromotesEntry() {
        // This is the difference between an LRU and a FIFO, and it is entirely
        // down to the access-order flag on the backing LinkedHashMap.
        LruCache<Integer, String> cache = new LruCache<>(3);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        cache.get(1);     // 1 is now the most recently used
        cache.put(4, "four");

        assertTrue(cache.containsKey(1), "key 1 was just read and must not be evicted");
        assertFalse(cache.containsKey(2), "key 2 is now the oldest");
    }

    @Test
    @DisplayName("access order places the least recently used first")
    void reportsAccessOrder() {
        LruCache<Integer, String> cache = new LruCache<>(5);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        cache.get(1);

        assertEquals(List.of(2, 3, 1), cache.keysInAccessOrder());
    }

    @Test
    @DisplayName("containsKey does not disturb the access order")
    void containsKeyDoesNotPromote() {
        // If it did, a test asserting eviction order would be measuring itself.
        LruCache<Integer, String> cache = new LruCache<>(5);
        cache.put(1, "one");
        cache.put(2, "two");

        cache.containsKey(1);

        assertEquals(List.of(1, 2), cache.keysInAccessOrder());
    }

    @Test
    @DisplayName("tracks hits, misses and the resulting hit rate")
    void tracksStatistics() {
        LruCache<String, String> cache = new LruCache<>(10);
        cache.put("a", "alpha");

        cache.get("a");
        cache.get("a");
        cache.get("b");

        assertEquals(2, cache.hitCount());
        assertEquals(1, cache.missCount());
        assertEquals(2.0 / 3.0, cache.hitRate(), 0.001);
    }

    @Test
    @DisplayName("the hit rate is zero before anything is looked up")
    void hitRateWithoutLookups() {
        // Not NaN: 0/0 would propagate into the performance report as "NaN%".
        assertEquals(0.0, new LruCache<String, String>(10).hitRate(), 0.0);
    }

    @Test
    @DisplayName("invalidate removes a single entry")
    void invalidateRemovesEntry() {
        LruCache<String, String> cache = new LruCache<>(10);
        cache.put("a", "alpha");
        cache.put("b", "bravo");

        cache.invalidate("a");

        assertFalse(cache.containsKey("a"));
        assertTrue(cache.containsKey("b"));
    }

    @Test
    @DisplayName("getOrLoad calls the loader only on a miss")
    void getOrLoadCachesResult() {
        LruCache<String, String> cache = new LruCache<>(10);
        AtomicInteger loaderCalls = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            cache.getOrLoad("key", key -> {
                loaderCalls.incrementAndGet();
                return "loaded";
            });
        }

        assertEquals(1, loaderCalls.get(), "the loader should have run exactly once");
        assertEquals(4, cache.hitCount());
        assertEquals(1, cache.missCount());
    }

    @Test
    @DisplayName("a null loader result is not cached, so it does not become a false hit")
    void nullResultNotCached() {
        LruCache<String, String> cache = new LruCache<>(10);

        assertEquals(null, cache.getOrLoad("missing", key -> null));
        assertFalse(cache.containsKey("missing"));
    }

    @Test
    @DisplayName("a null key or value is ignored rather than stored")
    void rejectsNulls() {
        LruCache<String, String> cache = new LruCache<>(10);
        cache.put(null, "value");
        cache.put("key", null);

        // A stored null would be indistinguishable from a miss on the way out,
        // turning every later lookup of that key into a false miss.
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("a capacity below one is rejected")
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<String, String>(0));
    }

    @Test
    @DisplayName("resetStats clears the counters but keeps the entries")
    void resetStatsKeepsEntries() {
        LruCache<String, String> cache = new LruCache<>(10);
        cache.put("a", "alpha");
        cache.get("a");

        cache.resetStats();

        assertEquals(0, cache.hitCount());
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("concurrent access does not corrupt the cache or lose entries")
    void survivesConcurrentAccess() throws Exception {
        // In access order even a get() mutates the linked list, so an
        // unsynchronised LinkedHashMap would corrupt here, not merely race.
        LruCache<Integer, String> cache = new LruCache<>(100);
        int threads = 8;
        int operationsPerThread = 2_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int seed = t;
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        int key = (seed * operationsPerThread + i) % 150;
                        cache.put(key, "value-" + key);
                        cache.get(key);
                        if (i % 50 == 0) {
                            cache.invalidate(key);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        pool.shutdownNow();

        assertTrue(cache.size() <= cache.maxEntries(),
                "capacity was exceeded: " + cache.size() + " > " + cache.maxEntries());
        assertTrue(cache.hitCount() + cache.missCount() > 0);
    }
}
