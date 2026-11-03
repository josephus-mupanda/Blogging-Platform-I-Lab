package org.example.bloggingplatformi.model;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the model classes follow the same rules as the database constraints,
 * so a bad value fails early in Java with a clear message.
 */
class ModelInvariantsTest {

    @Nested
    @DisplayName("Tag")
    class Tags {

        @Test
        @DisplayName("names normalize to lower case, matching ck_tags_lower")
        void normalizesName() {
            assertEquals("java", Tag.of("Java").name());
            assertEquals("data-structures", Tag.of("  DATA-STRUCTURES  ").name());
        }

        @Test
        @DisplayName("a slug is derived when none is supplied")
        void derivesSlug() {
            assertEquals("machine-learning", Tag.of("Machine Learning").slug());
        }

        @Test
        @DisplayName("a blank name is rejected")
        void rejectsBlankName() {
            assertThrows(IllegalArgumentException.class, () -> Tag.of("   "));
        }
    }

    @Nested
    @DisplayName("Review")
    class Reviews {

        @Test
        @DisplayName("ratings outside 1-5 are rejected, matching ck_reviews_rating")
        void enforcesRatingBounds() {
            assertThrows(IllegalArgumentException.class,
                    () -> Review.of(1, 1, "ada", 0, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> Review.of(1, 1, "ada", 6, null, null));
        }

        @Test
        @DisplayName("ratings at the boundaries are accepted")
        void acceptsBoundaryRatings() {
            assertEquals(1, Review.of(1, 1, "ada", 1, null, null).rating());
            assertEquals(5, Review.of(1, 1, "ada", 5, null, null).rating());
        }

        @Test
        @DisplayName("criteria defaults to an empty map rather than null")
        void criteriaNeverNull() {
            assertNotNull(Review.of(1, 1, "ada", 4, null, null).criteria());
            assertTrue(Review.of(1, 1, "ada", 4, null, null).criteria().isEmpty());
        }
    }

    @Nested
    @DisplayName("Comment")
    class Comments {

        @Test
        @DisplayName("a root comment sits at depth 0 on the root path")
        void rootDefaults() {
            Comment root = Comment.newRoot(42, 7L, "ada", "Interesting.");

            assertEquals(Comment.ROOT_PATH, root.path());
            assertEquals(0, root.depth());
            assertTrue(root.isRoot());
            assertFalse(root.isPersisted());
        }

        @Test
        @DisplayName("a reply extends its parent's path and depth")
        void replyInheritsPath() {
            // The path lists the ancestor ids, so a reply tree can be found with one prefix match.
            Comment root = Comment.newRoot(42, 7L, "ada", "Question?").withId("aaa");
            Comment reply = Comment.newReply(root, 8L, "grace", "Answer.");

            assertEquals("/aaa/", reply.path());
            assertEquals(1, reply.depth());
            assertEquals("aaa", reply.parentId());
            assertEquals(42, reply.postId());
        }

        @Test
        @DisplayName("a reply nests further still, one level per ancestor")
        void nestedReplyExtendsFurther() {
            Comment root = Comment.newRoot(42, 7L, "ada", "One").withId("aaa");
            Comment first = Comment.newReply(root, 8L, "grace", "Two").withId("bbb");
            Comment second = Comment.newReply(first, 9L, "linus", "Three");

            assertEquals("/aaa/bbb/", second.path());
            assertEquals(2, second.depth());
        }

        @Test
        @DisplayName("replying to an unsaved comment is rejected")
        void cannotReplyToUnsavedComment() {
            // The parent has no id yet, so there is nothing to add to the reply's path.
            Comment unsaved = Comment.newRoot(42, 7L, "ada", "Not saved yet.");

            assertThrows(IllegalArgumentException.class,
                    () -> Comment.newReply(unsaved, 8L, "grace", "Reply."));
        }

        @Test
        @DisplayName("a null author renders as a deleted user, not as a blank")
        void orphanedCommentsRenderSafely() {
            // comments.author_id is set to NULL when the user is deleted, so the comment stays.
            Comment orphan = new Comment("1", 42, null, null, null, "/", 0,
                    "Still here.", CommentStatus.VISIBLE, Map.of(), null, null);

            assertTrue(orphan.isOrphaned());
            assertEquals("[deleted user]", orphan.displayName());
        }
    }

    @Nested
    @DisplayName("Post")
    class Posts {

        @Test
        @DisplayName("publishing stamps a date, as ck_posts_published_at requires")
        void publishStampsDate() {
            Post draft = Post.builder()
                    .authorId(1).title("T").slug("t").body("B")
                    .status(PostStatus.DRAFT)
                    .build();

            Post published = draft.publish(null);

            assertEquals(PostStatus.PUBLISHED, published.status());
            assertNotNull(published.publishedAt());
        }

        @Test
        @DisplayName("unpublishing clears the date, which the same constraint requires")
        void unpublishClearsDate() {
            Post published = Post.builder()
                    .authorId(1).title("T").slug("t").body("B")
                    .status(PostStatus.DRAFT)
                    .build()
                    .publish(null);

            Post draft = published.unpublish();

            assertEquals(PostStatus.DRAFT, draft.status());
            assertNull(draft.publishedAt(), "a non-published row must not carry a publication date");
        }

        @Test
        @DisplayName("a negative view count is rejected")
        void rejectsNegativeViewCount() {
            assertThrows(IllegalArgumentException.class, () -> Post.builder()
                    .authorId(1).title("T").slug("t").body("B")
                    .viewCount(-1)
                    .build());
        }
    }
}
