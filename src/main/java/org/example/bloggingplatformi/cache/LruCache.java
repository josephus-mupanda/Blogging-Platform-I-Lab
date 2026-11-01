package org.example.bloggingplatformi.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A bounded least-recently-used cache.
 *
 * <p>Built on {@link LinkedHashMap} in <em>access order</em>. That one
 * constructor flag is what makes this an LRU rather than a FIFO: in access
 * order the map moves an entry to the back of its iteration order every time
 * it is read, so the entry at the front is always the one untouched for
 * longest. {@link LinkedHashMap#removeEldestEntry} is then called after each
 * insertion and evicts that front entry once the map is over capacity.
 *
 * <p><b>Complexity.</b> {@code get} and {@code put} are O(1): the hash map
 * gives constant-time location, and the intrusive doubly-linked list gives
 * constant-time reordering. Nothing here scans.
 *
 * <p><b>Thread safety.</b> Every method is synchronised on the instance.
 * {@code LinkedHashMap} is not thread-safe, and in access order even a
 * <em>read</em> mutates the list, so concurrent {@code get} calls would corrupt
 * it. A synchronised wrapper is not enough for the same reason.
 *
 * <p>Hit and miss counters are kept so Phase 4 can report a real hit rate
 * rather than assert that caching helped.
 */
public class LruCache<K, V> {

    private final int maxEntries;
    private final LinkedHashMap<K, V> entries;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public LruCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be at least 1, was " + maxEntries);
        }
        this.maxEntries = maxEntries;

        // initialCapacity sized so the map does not rehash on its way to
        // capacity; 0.75f is the default load factor; true selects access
        // order, which is the whole point.
        this.entries = new LinkedHashMap<>(Math.max(16, maxEntries * 4 / 3 + 1), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean evict = size() > LruCache.this.maxEntries;
                if (evict) {
                    evictions.incrementAndGet();
                }
                return evict;
            }
        };
    }

    /** Looks up a key, recording the result as a hit or a miss. */
    public synchronized Optional<V> get(K key) {
        V value = entries.get(key);
        if (value == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        return Optional.of(value);
    }

    /** True if present, without disturbing the access order or the counters. */
    public synchronized boolean containsKey(K key) {
        // containsKey does not promote the entry, which is what makes this
        // safe to use in a test that is checking eviction order.
        return entries.containsKey(key);
    }

    public synchronized void put(K key, V value) {
        if (key == null || value == null) {
            // A null value is indistinguishable from a miss on the way out, so
            // storing one would turn every later lookup into a false miss.
            return;
        }
        entries.put(key, value);
    }

    /**
     * Returns the cached value, or computes and stores it on a miss.
     *
     * <p>The loader runs while the lock is held. For this application that is
     * the right trade: the loader is a single indexed primary-key lookup, and
     * allowing concurrent misses to each run it would turn a cache stampede
     * into N identical queries.
     */
    public synchronized V getOrLoad(K key, Function<K, V> loader) {
        V existing = entries.get(key);
        if (existing != null) {
            hits.incrementAndGet();
            return existing;
        }
        misses.incrementAndGet();

        V loaded = loader.apply(key);
        if (loaded != null) {
            entries.put(key, loaded);
        }
        return loaded;
    }

    /** Removes one entry. This is the invalidation hook every write path uses. */
    public synchronized void invalidate(K key) {
        entries.remove(key);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    public long evictionCount() {
        return evictions.get();
    }

    /** Hit rate in [0, 1]; 0 when nothing has been looked up yet. */
    public double hitRate() {
        long h = hits.get();
        long total = h + misses.get();
        return total == 0 ? 0.0 : (double) h / total;
    }

    /** Resets the counters without discarding the entries, for a clean benchmark run. */
    public void resetStats() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
    }

    /** Keys from least to most recently used. Exposed for tests and diagnostics. */
    public synchronized java.util.List<K> keysInAccessOrder() {
        return java.util.List.copyOf(entries.keySet());
    }

    @Override
    public synchronized String toString() {
        return String.format("LruCache[size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%, evictions=%d]",
                entries.size(), maxEntries, hits.get(), misses.get(), hitRate() * 100, evictions.get());
    }
}
