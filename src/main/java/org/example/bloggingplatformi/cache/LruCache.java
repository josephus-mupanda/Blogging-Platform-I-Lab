package org.example.bloggingplatformi.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A fixed-size LRU (least recently used) cache.
 *
 * How it works: a LinkedHashMap in access order moves an entry to the back
 * each time it is read. So the front entry is the one unused for longest, and
 * it is removed when the cache is full. get and put are O(1).
 *
 * Methods are synchronized because in access order even a read changes the
 * map. It also counts hits and misses so we can report a hit rate.
 */
public class LruCache<K, V> {

    private final int maxEntries;
    private final LinkedHashMap<K, V> entries;

    private long hits;
    private long misses;
    private long evictions;

    public LruCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be at least 1, was " + maxEntries);
        }
        this.maxEntries = maxEntries;

        // 16 and 0.75f are the default size and load factor. true turns on
        // access order, which makes this an LRU.
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean evict = size() > LruCache.this.maxEntries;
                if (evict) {
                    evictions++;
                }
                return evict;
            }
        };
    }

    /** Looks up a key, recording the result as a hit or a miss. */
    public synchronized Optional<V> get(K key) {
        V value = entries.get(key);
        if (value == null) {
            misses++;
            return Optional.empty();
        }
        hits++;
        return Optional.of(value);
    }

    /** True if the key is cached. Does not change the access order or the counters. */
    public synchronized boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    public synchronized void put(K key, V value) {
        if (key == null || value == null) {
            // A stored null would look the same as a miss, so we skip it.
            return;
        }
        entries.put(key, value);
    }

    /**
     * Returns the cached value, or loads and stores it on a miss.
     * The loader runs inside the lock, so two threads never load the same key twice.
     */
    public synchronized V getOrLoad(K key, Function<K, V> loader) {
        V existing = entries.get(key);
        if (existing != null) {
            hits++;
            return existing;
        }
        misses++;

        V loaded = loader.apply(key);
        if (loaded != null) {
            entries.put(key, loaded);
        }
        return loaded;
    }

    /** Removes one entry. Called after every write so the cache is not stale. */
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

    public synchronized long hitCount() {
        return hits;
    }

    public synchronized long missCount() {
        return misses;
    }

    public synchronized long evictionCount() {
        return evictions;
    }

    /** Hit rate from 0 to 1. Returns 0 if nothing has been looked up yet. */
    public synchronized double hitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /** Resets the counters but keeps the entries. Used before a benchmark run. */
    public synchronized void resetStats() {
        hits = 0;
        misses = 0;
        evictions = 0;
    }

    /** Keys from least to most recently used. Used by tests. */
    public synchronized List<K> keysInAccessOrder() {
        return new ArrayList<>(entries.keySet());
    }

    @Override
    public synchronized String toString() {
        return String.format("LruCache[size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%, evictions=%d]",
                entries.size(), maxEntries, hits, misses, hitRate() * 100, evictions);
    }
}
