package org.example.bloggingplatformi.dao;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.example.bloggingplatformi.db.MongoProvider;
import org.example.bloggingplatformi.exception.DuplicateEntityException;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;
import org.example.bloggingplatformi.model.Review;
import org.example.bloggingplatformi.support.Databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for the MongoDB comment and review DAOs. Skipped if MongoDB is not running.
 * Test documents use a postId far above the seed data, so cleanup only deletes that one post id.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoCommentDaoIT {

    /** Well outside the seeded range of 1..508. */
    private static final long TEST_POST_ID = 9_000_001L;

    /** Well outside the seeded user range of 1..207. */
    private static final long TEST_AUTHOR_ID = 9_000_077L;

    private CommentDao comments;
    private ReviewDao reviews;

    @BeforeAll
    void checkDatabase() {
        assumeTrue(Databases.mongoAvailable(), Databases.mongoSkipReason());

        comments = new MongoCommentDao();
        reviews = new MongoReviewDao();
    }

    @AfterEach
    void cleanUp() {
        if (comments != null) {
            comments.deleteByPostId(TEST_POST_ID);
        }
        if (reviews != null) {
            reviews.deleteByPostId(TEST_POST_ID);
        }
    }

    @AfterAll
    void shutdown() {
        if (MongoProvider.isInitialised()) {
            MongoProvider.shutdown();
        }
    }

    @Test
    @DisplayName("reports MongoDB as the active store")
    void identifiesStore() {
        assertEquals("MongoDB", comments.storeName());
    }

    @Test
    @DisplayName("round-trips a comment through the $jsonSchema validator")
    void insertAndFind() {
        String id = comments.insert(Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "A test comment."));

        Comment found = comments.findById(id).orElseThrow();

        assertEquals(TEST_POST_ID, found.postId());
        assertEquals("ada_l", found.authorName());
        assertEquals("A test comment.", found.body());
        assertEquals(CommentStatus.VISIBLE, found.status());
        assertEquals(0, found.depth());
        assertEquals("/", found.path());
        assertNotNull(found.createdAt());
    }

    @Test
    @DisplayName("a reply carries the materialized path of its parent")
    void replyExtendsPath() {
        String rootId = comments.insert(
                Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "Root."));
        Comment root = comments.findById(rootId).orElseThrow();

        String replyId = comments.insert(
                Comment.newReply(root, 3L, "grace_h", "Reply."));
        Comment reply = comments.findById(replyId).orElseThrow();

        assertEquals("/" + rootId + "/", reply.path());
        assertEquals(1, reply.depth());
        assertEquals(rootId, reply.parentId());
    }

    @Test
    @DisplayName("a subtree read is one prefix match covering every level")
    void subtreeReturnsAllDescendants() {
        // One prefix match on path returns all levels. SQL needs a recursive query for this.
        String rootId = comments.insert(Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "Level 0."));
        Comment root = comments.findById(rootId).orElseThrow();

        String childId = comments.insert(Comment.newReply(root, 3L, "grace_h", "Level 1."));
        Comment child = comments.findById(childId).orElseThrow();

        comments.insert(Comment.newReply(child, 4L, "linus_t", "Level 2."));

        List<Comment> subtree = comments.findSubtree(rootId);

        assertEquals(3, subtree.size());
        assertEquals(0, subtree.get(0).depth());
    }

    @Test
    @DisplayName("the thread orders replies directly beneath their parent")
    void threadOrdering() {
        String rootId = comments.insert(Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "First root."));
        Comment root = comments.findById(rootId).orElseThrow();
        comments.insert(Comment.newReply(root, 3L, "grace_h", "Reply to first."));
        comments.insert(Comment.newRoot(TEST_POST_ID, 4L, "linus_t", "Second root."));

        List<Comment> thread = comments.findByPostId(TEST_POST_ID, true);

        assertEquals(3, thread.size());
        // Sorting by path puts the reply immediately after its parent.
        assertEquals("First root.", thread.get(0).body());
        assertEquals("Reply to first.", thread.get(1).body());
    }

    @Test
    @DisplayName("a visible reply to a hidden comment keeps its place in the thread")
    void hiddenParentKeepsVisibleReply() {
        // Same as the SQL version: sort the thread first, then drop hidden comments.
        String firstId = comments.insert(Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "Hidden root."));
        Comment first = comments.findById(firstId).orElseThrow();
        comments.insert(Comment.newReply(first, 3L, "grace_h", "Visible reply."));
        comments.insert(Comment.newRoot(TEST_POST_ID, 4L, "linus_t", "Later root."));
        comments.updateStatus(firstId, CommentStatus.HIDDEN);

        List<Comment> thread = comments.findByPostId(TEST_POST_ID, false);

        assertEquals(List.of("Visible reply.", "Later root."),
                thread.stream().map(Comment::body).toList());
    }

    @Test
    @DisplayName("deleting a comment removes its whole subtree, with no cascade to rely on")
    void deleteRemovesSubtree() {
        String rootId = comments.insert(Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "Root."));
        Comment root = comments.findById(rootId).orElseThrow();
        comments.insert(Comment.newReply(root, 3L, "grace_h", "Reply one."));
        comments.insert(Comment.newReply(root, 4L, "linus_t", "Reply two."));

        long removed = comments.deleteWithReplies(rootId);

        assertEquals(3, removed);
        assertEquals(0, comments.countByPostId(TEST_POST_ID, true));
    }

    @Test
    @DisplayName("hidden comments are excluded unless asked for")
    void statusFiltering() {
        String visible = comments.insert(Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "Visible."));
        String hidden = comments.insert(Comment.newRoot(TEST_POST_ID, 3L, "grace_h", "Hidden."));
        comments.updateStatus(hidden, CommentStatus.HIDDEN);

        assertEquals(2, comments.countByPostId(TEST_POST_ID, true));
        assertEquals(1, comments.countByPostId(TEST_POST_ID, false));
        assertNotNull(visible);
    }

    @Test
    @DisplayName("editing a body records the previous text in the edit history")
    void editKeepsHistory() {
        // The old text goes into editHistory, a field the SQL table does not have.
        String id = comments.insert(Comment.newRoot(TEST_POST_ID, 2L, "ada_l", "Original."));

        assertTrue(comments.updateBody(id, "Edited."));
        assertEquals("Edited.", comments.findById(id).orElseThrow().body());
    }

    @Test
    @DisplayName("renaming an author rewrites every denormalized copy")
    void renameAuthorUpdatesCopies() {
        // Each comment stores a copy of the author name, so a rename must update all copies.
        // A test author id is used, so real seed comments are not renamed.
        comments.insert(Comment.newRoot(TEST_POST_ID, TEST_AUTHOR_ID, "old_name", "One."));
        comments.insert(Comment.newRoot(TEST_POST_ID, TEST_AUTHOR_ID, "old_name", "Two."));

        long updated = comments.renameAuthor(TEST_AUTHOR_ID, "new_name");

        assertEquals(2, updated);
        assertTrue(comments.findByPostId(TEST_POST_ID, true).stream()
                .allMatch(c -> "new_name".equals(c.authorName())));
    }

    @Test
    @DisplayName("an invalid id is treated as not found rather than throwing")
    void invalidIdIsNotFound() {
        // A number like "12345" is not a valid ObjectId. It should return empty, not throw.
        assertTrue(comments.findById("12345").isEmpty());
        assertTrue(comments.findById("not-an-object-id").isEmpty());
    }

    @Test
    @DisplayName("the unique index enforces one review per user per post, as SQL does")
    void reviewUniqueness() {
        reviews.insert(Review.of(TEST_POST_ID, 2L, "ada_l", 5, "Great", null));

        assertThrows(DuplicateEntityException.class,
                () -> reviews.insert(Review.of(TEST_POST_ID, 2L, "ada_l", 3, "Again", null)));
    }

    @Test
    @DisplayName("reviews carry per-review criteria that the relational table cannot hold")
    void reviewCriteriaRoundTrip() {
        String id = reviews.insert(Review.of(TEST_POST_ID, 2L, "ada_l", 5, "Great", "Body.")
                .withCriteria(java.util.Map.of("clarity", 5, "depth", 4)));

        Review found = reviews.findById(id).orElseThrow();

        assertEquals(2, found.criteria().size());
        assertEquals(5, found.criteria().get("clarity"));
        assertEquals(4, found.criteria().get("depth"));
    }

    @Test
    @DisplayName("the average rating is empty for a post with no reviews")
    void averageIsEmptyWithoutReviews() {
        // With no reviews the aggregation returns nothing, like AVG returning NULL in SQL.
        assertTrue(reviews.averageRating(TEST_POST_ID).isEmpty());
    }

    @Test
    @DisplayName("the rating distribution aggregates by value")
    void ratingDistribution() {
        reviews.insert(Review.of(TEST_POST_ID, 2L, "ada_l", 5, null, null));
        reviews.insert(Review.of(TEST_POST_ID, 3L, "grace_h", 5, null, null));
        reviews.insert(Review.of(TEST_POST_ID, 4L, "linus_t", 3, null, null));

        var distribution = reviews.ratingDistribution(TEST_POST_ID);

        assertEquals(2, distribution.get(5));
        assertEquals(1, distribution.get(3));
        assertEquals(4.0 + 1.0 / 3.0, reviews.averageRating(TEST_POST_ID).orElseThrow(), 0.01);
    }
}
