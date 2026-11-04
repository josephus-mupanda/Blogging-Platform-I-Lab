package org.example.bloggingplatformi.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.bloggingplatformi.dao.PostDao;
import org.example.bloggingplatformi.dao.TagDao;
import org.example.bloggingplatformi.exception.EntityNotFoundException;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.PostSummary;
import org.example.bloggingplatformi.model.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the PostService rules using fake in-memory DAOs, with no database.
 * Covers slugs, excerpts, publish dates, tag cleanup and validation.
 * The real DAOs have their own integration tests.
 */
class PostServiceTest {

    private FakePostDao posts;
    private FakeTagDao tags;
    private PostService service;

    @BeforeEach
    void setUp() {
        posts = new FakePostDao();
        tags = new FakeTagDao();
        service = new PostService(posts, tags);
    }

    @Test
    @DisplayName("creating a post derives a slug and an excerpt")
    void createDerivesSlugAndExcerpt() {
        Post post = service.create(1, "Caching Without Lying",
                "An LRU cache is ten lines on top of LinkedHashMap.",
                PostStatus.DRAFT, List.of());

        assertEquals("caching-without-lying", post.slug());
        assertEquals("An LRU cache is ten lines on top of LinkedHashMap.", post.excerpt());
        assertTrue(post.isPersisted());
    }

    @Test
    @DisplayName("a colliding title gets a suffixed slug, not a constraint violation")
    void createUniquifiesSlug() {
        service.create(1, "Same Title", "First body.", PostStatus.DRAFT, List.of());
        Post second = service.create(1, "Same Title", "Second body.", PostStatus.DRAFT, List.of());
        Post third = service.create(1, "Same Title", "Third body.", PostStatus.DRAFT, List.of());

        assertEquals("same-title", posts.byId(1L).slug());
        assertEquals("same-title-2", second.slug());
        assertEquals("same-title-3", third.slug());
    }

    @Test
    @DisplayName("creating as PUBLISHED stamps a publication date")
    void publishedOnCreateCarriesDate() {
        // ck_posts_published_at rejects a PUBLISHED row with a null date.
        Post post = service.create(1, "Live Now", "Body.", PostStatus.PUBLISHED, List.of());

        assertEquals(PostStatus.PUBLISHED, post.status());
        assertNotNull(post.publishedAt());
    }

    @Test
    @DisplayName("creating as DRAFT leaves the publication date null")
    void draftHasNoDate() {
        // A draft has no publication date yet.
        Post post = service.create(1, "Not Yet", "Body.", PostStatus.DRAFT, List.of());

        assertNull(post.publishedAt());
    }

    @Test
    @DisplayName("status defaults to DRAFT when none is given")
    void defaultsToDraft() {
        assertEquals(PostStatus.DRAFT,
                service.create(1, "Untitled", "Body.", null, List.of()).status());
    }

    @Test
    @DisplayName("editing the body regenerates the excerpt")
    void updateRegeneratesExcerpt() {
        // The excerpt is a copy of the body's start, so it must be rebuilt after an edit.
        Post created = service.create(1, "Title", "Original body text.",
                PostStatus.DRAFT, List.of());

        Post updated = service.update(created.postId(), "Title", "Completely new body text.",
                PostStatus.DRAFT, List.of());

        assertEquals("Completely new body text.", updated.excerpt());
    }

    @Test
    @DisplayName("editing the title does not change the slug")
    void updateKeepsSlug() {
        // The slug is used in links, so changing it would break old links.
        Post created = service.create(1, "Original Title", "Body.",
                PostStatus.DRAFT, List.of());

        Post updated = service.update(created.postId(), "A Completely Different Title",
                "Body.", PostStatus.DRAFT, List.of());

        assertEquals("original-title", updated.slug());
    }

    @Test
    @DisplayName("publishing a draft stamps the date; unpublishing clears it")
    void publishAndUnpublishMaintainTheDate() {
        Post created = service.create(1, "Title", "Body.", PostStatus.DRAFT, List.of());

        Post published = service.publish(created.postId());
        assertNotNull(published.publishedAt());

        Post draft = service.unpublish(created.postId());
        assertEquals(PostStatus.DRAFT, draft.status());
        assertNull(draft.publishedAt());
    }

    @Test
    @DisplayName("re-publishing keeps the original publication date")
    void republishKeepsOriginalDate() {
        Post created = service.create(1, "Title", "Body.", PostStatus.PUBLISHED, List.of());
        var originalDate = created.publishedAt();

        Post updated = service.update(created.postId(), "Title", "Edited body.",
                PostStatus.PUBLISHED, List.of());

        assertEquals(originalDate, updated.publishedAt(),
                "editing a published post must not reset its publication date");
    }

    @Test
    @DisplayName("tag names are normalized and deduplicated")
    void tagsAreNormalizedAndDeduplicated() {
        // post_tags does not allow the same tag twice on one post, so duplicates are removed first.
        service.create(1, "Tagged", "Body.", PostStatus.DRAFT,
                List.of("Java", "java", "  JAVA  ", "PostgreSQL"));

        assertEquals(2, tags.created.size(), "expected 'java' and 'postgresql' only");
        assertTrue(tags.created.containsKey("java"));
        assertTrue(tags.created.containsKey("postgresql"));
        assertEquals(2, posts.tagsFor(1L).size());
    }

    @Test
    @DisplayName("blank tag entries are ignored")
    void blankTagsIgnored() {
        service.create(1, "Tagged", "Body.", PostStatus.DRAFT,
                List.of("java", "   ", ""));

        assertEquals(1, tags.created.size());
    }

    @Test
    @DisplayName("validation reports every problem at once")
    void validationCollectsAllProblems() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.create(0, "  ", "  ", PostStatus.DRAFT, List.of()));

        assertEquals(3, thrown.getErrors().size());
        assertTrue(thrown.getErrors().containsKey("title"));
        assertTrue(thrown.getErrors().containsKey("body"));
        assertTrue(thrown.getErrors().containsKey("author"));
    }

    @Test
    @DisplayName("an over-length title is rejected before it reaches the column")
    void rejectsOverlongTitle() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.create(1, "a".repeat(256), "Body.", PostStatus.DRAFT, List.of()));

        assertTrue(thrown.getErrors().containsKey("title"));
    }

    @Test
    @DisplayName("updating a post that does not exist reports it as not found")
    void updateMissingPost() {
        assertThrows(EntityNotFoundException.class,
                () -> service.update(999, "Title", "Body.", PostStatus.DRAFT, List.of()));
    }

    @Test
    @DisplayName("recording a view delegates to the atomic increment")
    void recordViewIncrements() {
        // The real DAO uses view_count = view_count + 1 in SQL, so two views at once both count.
        Post created = service.create(1, "Title", "Body.", PostStatus.DRAFT, List.of());

        service.recordView(created.postId());
        service.recordView(created.postId());

        assertEquals(2, posts.viewCounts.get(created.postId()));
    }

    @Test
    @DisplayName("passing null tags leaves existing assignments alone")
    void nullTagsLeaveAssignmentsAlone() {
        Post created = service.create(1, "Title", "Body.", PostStatus.DRAFT, List.of("java"));
        assertEquals(1, posts.tagsFor(created.postId()).size());

        service.publish(created.postId());

        assertEquals(1, posts.tagsFor(created.postId()).size(),
                "publishing must not wipe the post's tags");
    }

    // -----------------------------------------------------------------
    // In-memory fakes
    // -----------------------------------------------------------------

    private static final class FakePostDao implements PostDao {
        private final Map<Long, Post> store = new LinkedHashMap<>();
        private final Map<Long, Set<Integer>> tagLinks = new HashMap<>();
        private final Map<Long, Integer> viewCounts = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        Post byId(long id) {
            return store.get(id);
        }

        Set<Integer> tagsFor(long id) {
            return tagLinks.getOrDefault(id, Set.of());
        }

        @Override
        public Optional<Post> findById(long postId) {
            return Optional.ofNullable(store.get(postId));
        }

        @Override
        public Optional<Post> findBySlug(String slug) {
            return store.values().stream().filter(p -> p.slug().equals(slug)).findFirst();
        }

        @Override
        public Page<PostSummary> search(PostFilter filter) {
            return Page.empty(filter.pageNumber(), filter.pageSize());
        }

        @Override
        public boolean existsBySlug(String slug) {
            return store.values().stream().anyMatch(p -> p.slug().equals(slug));
        }

        @Override
        public long insertWithTags(Post post, Set<Integer> tagIds) {
            long id = sequence.incrementAndGet();
            store.put(id, post.withId(id));
            viewCounts.put(id, 0);
            if (tagIds != null) {
                tagLinks.put(id, new java.util.LinkedHashSet<>(tagIds));
            }
            return id;
        }

        @Override
        public boolean updateWithTags(Post post, Set<Integer> tagIds) {
            if (!store.containsKey(post.postId())) {
                return false;
            }
            store.put(post.postId(), post);
            if (tagIds != null) {
                tagLinks.put(post.postId(), new java.util.LinkedHashSet<>(tagIds));
            }
            return true;
        }

        @Override
        public boolean deleteById(long postId) {
            tagLinks.remove(postId);
            return store.remove(postId) != null;
        }

        @Override
        public boolean incrementViewCount(long postId) {
            if (!store.containsKey(postId)) {
                return false;
            }
            viewCounts.merge(postId, 1, Integer::sum);
            return true;
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public long countByStatus(String status) {
            return store.values().stream()
                    .filter(p -> p.status().name().equals(status))
                    .count();
        }
    }

    private static final class FakeTagDao implements TagDao {
        private final Map<String, Integer> created = new LinkedHashMap<>();
        private int sequence;

        @Override
        public Optional<Tag> findById(int tagId) {
            return Optional.empty();
        }

        @Override
        public Optional<Tag> findByName(String name) {
            Integer id = created.get(name.strip().toLowerCase(Locale.ROOT));
            return id == null ? Optional.empty() : Optional.of(Tag.of(name).withId(id));
        }

        @Override
        public List<Tag> findAll() {
            return new ArrayList<>();
        }

        @Override
        public List<Tag> findByPostId(long postId) {
            return List.of();
        }

        @Override
        public Map<String, Long> usageCounts(int limit) {
            return Map.of();
        }

        @Override
        public long insert(Tag tag) {
            return findOrCreate(tag.name());
        }

        @Override
        public boolean deleteById(int tagId) {
            return created.values().remove(tagId);
        }

        @Override
        public int findOrCreate(String name) {
            // Like the real DAO: names are lower-cased, so "Java" and "java" get the same id.
            String normalized = name.strip().toLowerCase(Locale.ROOT);
            return created.computeIfAbsent(normalized, key -> ++sequence);
        }

        @Override
        public long count() {
            return created.size();
        }
    }
}
