package org.example.bloggingplatformi.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.exception.DuplicateEntityException;
import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.PostSort;
import org.example.bloggingplatformi.model.PostSummary;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.support.Databases;
import org.example.bloggingplatformi.util.Excerpts;
import org.example.bloggingplatformi.util.Slugs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for the PostgreSQL DAOs, run against a real database.
 * If PostgreSQL is not running, the tests are skipped, not failed.
 * Each test creates its own rows and deletes them at the end, so the seed data stays the same.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresDaoIT {

    private UserDao users;
    private PostDao posts;
    private TagDao tags;
    private CommentDao comments;
    private ReviewDao reviews;

    private long authorId;

    @BeforeAll
    void checkDatabase() {
        assumeTrue(Databases.postgresAvailable(), Databases.postgresSkipReason());

        users = new JdbcUserDao();
        posts = new JdbcPostDao();
        tags = new JdbcTagDao();
        comments = new JdbcCommentDao();
        reviews = new JdbcReviewDao();
    }

    @BeforeEach
    void createAuthor() {
        // A unique suffix, so the username is new on every run.
        String suffix = String.valueOf(System.nanoTime());
        authorId = users.insert(User.newUser(
                "it_user_" + suffix,
                "it_" + suffix + "@test.local",
                "$2a$10$integrationtestplaceholderhash000000000000000000000",
                "Integration Test User",
                null,
                Role.AUTHOR));
    }

    @AfterAll
    void shutdown() {
        if (Database.isInitialised()) {
            Database.shutdown();
        }
    }

    private void deleteAuthor() {
        users.deleteById(authorId);
    }

    @Nested
    @DisplayName("Users")
    class Users {

        @Test
        @DisplayName("round-trips an insert and a lookup")
        void insertAndFind() {
            try {
                Optional<User> found = users.findById(authorId);

                assertTrue(found.isPresent());
                assertEquals("Integration Test User", found.get().displayName());
                assertEquals(Role.AUTHOR, found.get().role());
                assertTrue(found.get().active());
                assertNotNull(found.get().createdAt(), "created_at should come from DEFAULT now()");
            } finally {
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("username lookup ignores case")
        void findByUsernameIsCaseInsensitive() {
            try {
                User user = users.findById(authorId).orElseThrow();

                assertTrue(users.findByUsername(user.username().toUpperCase()).isPresent(),
                        "LOWER(username) = LOWER(?) should match regardless of case");
            } finally {
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("uq_users_username rejects a duplicate")
        void duplicateUsernameRejected() {
            try {
                User existing = users.findById(authorId).orElseThrow();

                DuplicateEntityException thrown = assertThrows(DuplicateEntityException.class,
                        () -> users.insert(User.newUser(
                                existing.username(),
                                "different_" + System.nanoTime() + "@test.local",
                                "hash", "Another", null, Role.READER)));

                assertEquals("username", thrown.getField());
            } finally {
                deleteAuthor();
            }
        }
    }

    @Nested
    @DisplayName("Posts")
    class Posts {

        @Test
        @DisplayName("insert with tags, read back, then delete")
        void crudRoundTrip() {
            long postId = 0;
            // Test-only tag names, deleted at the end, so the app's real tags are not touched.
            String suffix = String.valueOf(System.nanoTime());
            int javaTag = 0;
            int sqlTag = 0;
            try {
                javaTag = tags.findOrCreate("it-java-" + suffix);
                sqlTag = tags.findOrCreate("it-sql-" + suffix);

                String body = "Integration test body for the post DAO round trip.";
                Post post = Post.builder()
                        .authorId(authorId)
                        .title("Integration Round Trip")
                        .slug(Slugs.uniquify(Slugs.forPost("Integration Round Trip " + System.nanoTime()),
                                Slugs.MAX_POST_SLUG_LENGTH, posts::existsBySlug))
                        .body(body)
                        .excerpt(Excerpts.from(body))
                        .status(PostStatus.DRAFT)
                        .build();

                postId = posts.insertWithTags(post, Set.of(javaTag, sqlTag));

                Optional<Post> found = posts.findById(postId);
                assertTrue(found.isPresent());
                assertEquals("Integration Round Trip", found.get().title());
                assertEquals(PostStatus.DRAFT, found.get().status());
                assertEquals(2, tags.findByPostId(postId).size());
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                tags.deleteById(javaTag);
                tags.deleteById(sqlTag);
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("the view counter increments atomically")
        void incrementViewCount() {
            long postId = 0;
            try {
                postId = insertSimplePost("View Counter");
                int before = posts.findById(postId).orElseThrow().viewCount();

                posts.incrementViewCount(postId);
                posts.incrementViewCount(postId);
                posts.incrementViewCount(postId);

                assertEquals(before + 3, posts.findById(postId).orElseThrow().viewCount());
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("uq_posts_slug rejects a duplicate slug")
        void duplicateSlugRejected() {
            long postId = 0;
            try {
                postId = insertSimplePost("Duplicate Slug Test");
                String slug = posts.findById(postId).orElseThrow().slug();

                final long ignored = postId;
                assertThrows(DuplicateEntityException.class, () -> posts.insertWithTags(
                        Post.builder()
                                .authorId(authorId)
                                .title("Another")
                                .slug(slug)
                                .body("Body.")
                                .status(PostStatus.DRAFT)
                                .build(),
                        Set.of()));
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("search filters by author and paginates")
        void searchFiltersAndPaginates() {
            long first = 0;
            long second = 0;
            try {
                first = insertSimplePost("Search One");
                second = insertSimplePost("Search Two");

                PostFilter filter = PostFilter.all()
                        .withAuthorId(authorId)
                        .withPageSize(1);

                Page<PostSummary> page = posts.search(filter);

                assertEquals(2, page.totalElements());
                assertEquals(2, page.totalPages());
                assertEquals(1, page.content().size());
                assertTrue(page.hasNext());
                assertFalse(page.hasPrevious());

                Page<PostSummary> next = posts.search(filter.nextPage());
                assertEquals(1, next.content().size());
                assertTrue(next.hasPrevious());

                // Page 2 must not repeat the row from page 1.
                assertFalse(page.content().get(0).postId() == next.content().get(0).postId(),
                        "page 2 repeated a row from page 1, so the sort is not deterministic");
            } finally {
                if (first > 0) {
                    posts.deleteById(first);
                }
                if (second > 0) {
                    posts.deleteById(second);
                }
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("keyword search is case-insensitive")
        void searchIsCaseInsensitive() {
            long postId = 0;
            try {
                postId = insertSimplePost("Zygomorphic Indexing Treatise");

                Page<PostSummary> lower = posts.search(PostFilter.all()
                        .withAuthorId(authorId).withKeyword("zygomorphic"));
                Page<PostSummary> upper = posts.search(PostFilter.all()
                        .withAuthorId(authorId).withKeyword("ZYGOMORPHIC"));

                assertEquals(1, lower.totalElements());
                assertEquals(1, upper.totalElements(),
                        "ILIKE should match regardless of case");
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("every sort option produces a valid query")
        void everySortExecutes() {
            long postId = 0;
            try {
                postId = insertSimplePost("Sort Coverage");

                // Each sort adds its own ORDER BY text, so a typo only shows up when the query runs.
                for (PostSort sort : PostSort.values()) {
                    Page<PostSummary> page = posts.search(
                            PostFilter.all().withAuthorId(authorId).withSort(sort));
                    assertEquals(1, page.totalElements(), "sort " + sort + " failed");
                }
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("deleting a post cascades to its comments and reviews")
        void deleteCascades() {
            long postId = 0;
            try {
                postId = insertSimplePost("Cascade Test");

                comments.insert(org.example.bloggingplatformi.model.Comment.newRoot(
                        postId, authorId, "Integration Test User", "A comment."));
                reviews.insert(org.example.bloggingplatformi.model.Review.of(
                        postId, authorId, "Integration Test User", 5, "Good", null));

                assertEquals(1, comments.countByPostId(postId, true));
                assertEquals(1, reviews.countByPostId(postId));

                posts.deleteById(postId);

                assertEquals(0, comments.countByPostId(postId, true),
                        "ON DELETE CASCADE should have removed the comments");
                assertEquals(0, reviews.countByPostId(postId),
                        "ON DELETE CASCADE should have removed the reviews");
                postId = 0;
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }
    }

    @Nested
    @DisplayName("Comments")
    class Comments {

        @Test
        @DisplayName("a reply nests beneath its parent in the thread")
        void threadOrdersRepliesUnderParents() {
            long postId = 0;
            try {
                postId = insertSimplePost("Threading Test");

                var root = comments.findById(comments.insert(
                        org.example.bloggingplatformi.model.Comment.newRoot(
                                postId, authorId, "Integration Test User", "Root question?")))
                        .orElseThrow();
                comments.insert(org.example.bloggingplatformi.model.Comment.newReply(
                        root, authorId, "Integration Test User", "A reply."));

                List<org.example.bloggingplatformi.model.Comment> thread =
                        comments.findByPostId(postId, true);

                assertEquals(2, thread.size());
                assertEquals(0, thread.get(0).depth(), "the root should come first");
                assertEquals(1, thread.get(1).depth(), "the reply should follow, one level deeper");
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("deleting a comment removes its replies through the self-referencing cascade")
        void deleteRemovesSubtree() {
            long postId = 0;
            try {
                postId = insertSimplePost("Subtree Delete");

                var root = comments.findById(comments.insert(
                        org.example.bloggingplatformi.model.Comment.newRoot(
                                postId, authorId, "Integration Test User", "Root.")))
                        .orElseThrow();
                comments.insert(org.example.bloggingplatformi.model.Comment.newReply(
                        root, authorId, "Integration Test User", "Reply one."));
                comments.insert(org.example.bloggingplatformi.model.Comment.newReply(
                        root, authorId, "Integration Test User", "Reply two."));

                long removed = comments.deleteWithReplies(root.commentId());

                assertEquals(3, removed, "the root and both replies should go");
                assertEquals(0, comments.countByPostId(postId, true));
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }
    }

    @Nested
    @DisplayName("Reviews")
    class Reviews {

        @Test
        @DisplayName("uq_reviews_post_reviewer enforces one review per user per post")
        void oneReviewPerUserPerPost() {
            long postId = 0;
            try {
                postId = insertSimplePost("Review Uniqueness");
                reviews.insert(org.example.bloggingplatformi.model.Review.of(
                        postId, authorId, "Integration Test User", 4, "First", null));

                final long id = postId;
                assertThrows(DuplicateEntityException.class, () -> reviews.insert(
                        org.example.bloggingplatformi.model.Review.of(
                                id, authorId, "Integration Test User", 2, "Second", null)));
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }

        @Test
        @DisplayName("average rating is empty for a post with no reviews")
        void averageIsEmptyWithoutReviews() {
            long postId = 0;
            try {
                postId = insertSimplePost("No Reviews Yet");

                // AVG over no rows is NULL. It should come back empty, not as 0.0.
                assertTrue(reviews.averageRating(postId).isEmpty());
            } finally {
                if (postId > 0) {
                    posts.deleteById(postId);
                }
                deleteAuthor();
            }
        }
    }

    /** Inserts a simple published post by the test author. */
    private long insertSimplePost(String title) {
        String body = title + " body text for integration testing purposes.";
        return posts.insertWithTags(Post.builder()
                .authorId(authorId)
                .title(title)
                .slug(Slugs.uniquify(Slugs.forPost(title + " " + System.nanoTime()),
                        Slugs.MAX_POST_SLUG_LENGTH, posts::existsBySlug))
                .body(body)
                .excerpt(Excerpts.from(body))
                .status(PostStatus.PUBLISHED)
                .publishedAt(java.time.OffsetDateTime.now())
                .build(), Set.of());
    }
}
