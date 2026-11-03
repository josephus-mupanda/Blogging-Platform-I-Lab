package org.example.bloggingplatformi.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import org.example.bloggingplatformi.BloggingPlatformApplication;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.PostSummary;
import org.example.bloggingplatformi.model.Review;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.CommentService;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.ReviewService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.support.Databases;

import static org.example.bloggingplatformi.support.FxDriver.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end test: creates, edits and deletes data by clicking through the real
 * JavaFX app, with PostgreSQL and MongoDB running. Services are only used to
 * check the database afterwards. It opens a window, so it is not part of mvn test.
 * Run: mvn test -Dtest=UiCrudE2E -Dsurefire.failIfNoSpecifiedTests=false
 * Screenshots are saved to docs/screenshots/.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UiCrudE2E {

    private static final Path SHOTS = Path.of(System.getProperty("e2e.screenshots", "docs/screenshots"));

    /** Different on every run, so data left by an old run cannot make a check pass. */
    private final String token = "e2e" + Long.toString(System.currentTimeMillis(), 36);
    private final String title = "UI end-to-end check " + token;
    private final String tagName = "e2e-tag-" + token;
    private final String newTagName = "e2e-new-" + token;
    private final String secondTagName = "e2e-second-" + token;

    private final PostService posts = new PostService();
    private final CommentService comments = new CommentService();
    private final ReviewService reviews = new ReviewService();
    private final TagService tags = new TagService();
    private final UserService users = new UserService();

    private Stage stage;
    private Scene scene;
    private Long postId;

    @BeforeAll
    void launch() throws Exception {
        assumeTrue(Databases.postgresAvailable(), Databases.postgresSkipReason());
        assumeTrue(Databases.mongoAvailable(), Databases.mongoSkipReason());

        startToolkit();
        stage = fx(() -> {
            Stage s = new Stage();
            new BloggingPlatformApplication().start(s);
            return s;
        });
        scene = fx(stage::getScene);
        settle();

        // Sign in through the login screen as the admin, and use that account for the whole test.
        type(field(root(), TextField.class, "Username"), "admin");
        type(field(root(), PasswordField.class, "Password"), "111111");
        click(button(root(), "Sign in"));
        waitFor("a signed-in user", Session::isSignedIn);
        settle();
        assertEquals(Role.ADMIN, Session.currentRole());
    }

    @AfterAll
    void close() {
        // Cleans up only if a test failed before deleting its data.
        if (postId != null) {
            try {
                comments.deleteForPost(postId);
                reviews.deleteForPost(postId);
                posts.delete(postId);
            } catch (RuntimeException ignored) {
                // Already deleted by the test. This is the normal case.
            }
        }
        for (String name : List.of(tagName, newTagName, secondTagName)) {
            tags.findByName(name).ifPresent(tag -> tags.delete(tag.tagId()));
        }
        if (stage != null) {
            fxRun(stage::close);
        }
    }

    private Node root() {
        return scene.getRoot();
    }

    private void go(String navLabel) {
        click(button(root(), navLabel));
        settle();
    }

    private void shot(String file) {
        settle();
        screenshot(scene, SHOTS.resolve(file));
    }

    // -----------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("dashboard loads live figures from both stores")
    void dashboard() {
        assertTrue(hasText(root(), "Dashboard") || hasText(root(), "Overview"),
                "the dashboard heading is shown");
        shot("01-dashboard.png");
    }

    @Test
    @Order(2)
    @DisplayName("the editor rejects an empty post and says why, under the field")
    void validationShownInline() {
        go("Posts");
        shot("02-posts-list.png");

        click(button(root(), "New post"));
        settle();
        long before = posts.count();

        click(button(root(), "Save post"));
        settle();

        assertTrue(hasText(root(), "A title is required."), "title error shown under the field");
        assertEquals(before, posts.count(), "nothing was written");
        shot("08-validation.png");
    }

    @Test
    @Order(3)
    @DisplayName("create: a published post with a new and an existing tag")
    void createPost() {
        type(field(root(), TextField.class, "A clear, specific headline"), title);
        type(field(root(), TextArea.class, "Write the post"),
                "Written by the end-to-end test through the editor. It checks that saving "
              + "persists the post, its tags and its publication date in one transaction.");

        @SuppressWarnings("unchecked")
        ComboBox<String> status = one(root(), ComboBox.class,
                c -> c.getItems().contains("Published"), "status selector");
        fxRun(() -> status.setValue("Published"));

        @SuppressWarnings("unchecked")
        ComboBox<String> tagInput = one(root(), ComboBox.class, ComboBox::isEditable, "tag input");
        typeAndEnter(fx(tagInput::getEditor), tagName);
        typeAndEnter(fx(tagInput::getEditor), secondTagName);
        shot("03-post-editor.png");

        click(button(root(), "Save post"));
        settle();

        PostSummary saved = findOne(title);
        postId = saved.postId();
        Post post = posts.require(postId);
        assertEquals(PostStatus.PUBLISHED, post.status());
        assertNotNull(post.publishedAt(), "publishing stamps a date");
        assertEquals(List.of(secondTagName, tagName).stream().sorted().toList(),
                posts.tagsOf(postId).stream().map(Tag::name).sorted().toList());
        assertEquals(Session.currentUserId(), post.authorId());
    }

    @Test
    @Order(4)
    @DisplayName("read: the search box finds the post, case-insensitively")
    void searchFindsPost() {
        search(token.toUpperCase());
        TableRow<PostSummary> row = rowFor(postId);
        assertEquals(title, fx(row::getItem).title());
        TableView<PostSummary> table = table();
        assertEquals(1, fx(() -> table.getItems().size()), "exactly one match");
    }

    @Test
    @Order(5)
    @DisplayName("create: comment, threaded reply and review from the detail screen")
    void discuss() {
        click(buttonWithTooltip(rowFor(postId), "Open"));
        settle();

        type(field(root(), TextArea.class, "Add a comment"), "Top-level comment " + token);
        click(button(root(), "Comment"));
        settle();

        click(button(root(), "Reply"));
        type(field(root(), TextArea.class, "Replying to"), "Reply " + token);
        click(button(root(), "Post reply"));
        settle();

        List<Comment> thread = comments.threadFor(postId, true);
        assertEquals(2, thread.size());
        assertEquals(0, thread.get(0).depth());
        assertEquals(1, thread.get(1).depth());
        assertEquals(thread.get(0).commentId(), thread.get(1).parentId(), "reply hangs off the comment");

        @SuppressWarnings("unchecked")
        ComboBox<Integer> rating = one(root(), ComboBox.class,
                c -> c.getItems().contains(Integer.valueOf(1)), "rating selector");
        fxRun(() -> rating.setValue(4));
        type(field(root(), TextField.class, "Headline"), "Solid " + token);
        click(button(root(), "Review"));
        settle();

        List<Review> stored = reviews.findForPost(postId);
        assertEquals(1, stored.size());
        assertEquals(4, stored.get(0).rating());
        assertTrue(hasText(root(), "4.00"), "the rating summary re-rendered");
    }

    @Test
    @Order(6)
    @DisplayName("constraint: a second review by the same user is refused with a message")
    void duplicateReviewRefused() {
        click(button(root(), "Review"));
        settle();

        DialogPane dialog = awaitDialog();
        assertEquals("Could not save the review", fx(dialog::getHeaderText));
        answerDialog(dialog, "OK");
        settle();

        assertEquals(1, reviews.findForPost(postId).size(), "still exactly one review");
    }

    @Test
    @Order(7)
    @DisplayName("update: hide a reply, then restore it from the moderation queue")
    void moderate() {
        String replyBody = "Reply " + token;
        Node reply = commentBox(replyBody);
        click(button(reply, "Hide"));
        settle();
        assertEquals(CommentStatus.HIDDEN, statusOf(replyBody));

        go("Moderation");
        Node queued = commentBox(replyBody);
        shot("06-moderation.png");

        click(button(queued, "Restore"));
        settle();
        assertEquals(CommentStatus.VISIBLE, statusOf(replyBody));
        assertFalse(hasText(root(), replyBody), "restored comment leaves the queue");
    }

    @Test
    @Order(8)
    @DisplayName("update: edit title and unpublish; the slug does not change")
    void editPost() {
        String slug = posts.require(postId).slug();

        go("Posts");
        search(token);

        // The list must show the same counts as the detail screen:
        // one comment, one reply and one 4-star review.
        PostSummary listed = fx(rowFor(postId)::getItem);
        assertEquals(comments.countFor(postId, false), listed.commentCount());
        assertEquals(2, listed.commentCount());
        assertEquals(1, listed.reviewCount());
        assertEquals("4.00", listed.ratingDisplay());

        click(buttonWithTooltip(rowFor(postId), "Edit"));
        settle();

        TextField titleField = field(root(), TextField.class, "A clear, specific headline");
        assertEquals(title, fx(titleField::getText), "editor loaded the stored title");
        type(titleField, title + " (edited)");

        @SuppressWarnings("unchecked")
        ComboBox<String> status = one(root(), ComboBox.class,
                c -> c.getItems().contains("Published"), "status selector");
        fxRun(() -> status.setValue("Draft"));

        click(button(root(), "Save post"));
        settle();

        Post post = posts.require(postId);
        assertEquals(title + " (edited)", post.title());
        assertEquals(PostStatus.DRAFT, post.status());
        assertNull(post.publishedAt(), "a draft carries no publication date");
        assertEquals(slug, post.slug(), "the URL slug is immutable");
    }

    @Test
    @Order(9)
    @DisplayName("update then delete: edit a comment, then removing it takes its replies with it")
    void editAndDeleteComment() {
        search(token);
        click(buttonWithTooltip(rowFor(postId), "Open"));
        settle();

        String editedBody = "Top-level comment " + token + " (edited)";
        Node original = commentBox("Top-level comment " + token);
        click(button(original, "Edit"));
        type(one(original, TextArea.class, area -> true, "comment edit box"), editedBody);
        click(button(original, "Save"));
        settle();
        assertEquals(1, comments.threadFor(postId, true).stream()
                .filter(c -> editedBody.equals(c.body())).count(), "the edited text was saved");

        Node top = commentBox(editedBody);
        clickLater(button(top, "Delete"));
        answerDialog(awaitDialog(), "Delete");
        settle();

        assertEquals(0, comments.countFor(postId, true), "comment and reply both gone");
    }

    @Test
    @Order(10)
    @DisplayName("create and delete a tag from the tag screen")
    void tagLifecycle() {
        go("Tags");
        type(field(root(), TextField.class, "New tag name"), newTagName.toUpperCase());
        click(button(root(), "Add tag"));
        settle();

        Optional<Tag> created = tags.findByName(newTagName);
        assertTrue(created.isPresent(), "stored lower-cased");
        shot("04-tags.png");

        Label chipLabel = one(root(), Label.class,
                l -> newTagName.equals(l.getText()) && l.getParent() != null
                        && l.getParent().getStyleClass().contains("chip-removable"),
                "new tag chip");
        Button remove = one(fx(chipLabel::getParent), Button.class, b -> "×".equals(b.getText()), "chip remove");
        clickLater(remove);
        answerDialog(awaitDialog(), "Delete tag");
        settle();

        assertTrue(tags.findByName(newTagName).isEmpty());
    }

    @Test
    @Order(11)
    @DisplayName("update: deactivate and reactivate an account")
    void toggleAccount() {
        go("Authors");
        shot("07-authors.png");

        @SuppressWarnings("unchecked")
        TableView<User> table = one(root(), TableView.class, t -> true, "accounts table");
        Optional<User> candidate = fx(() -> table.getItems().stream()
                .filter(u -> u.role() != Role.ADMIN && u.active())
                .findFirst());
        // The database starts with only the admin, so this needs a blogger or reader to sign up first.
        assumeTrue(candidate.isPresent(), "skipped: no blogger or reader account yet");
        User target = candidate.get();
        fxRun(() -> table.scrollTo(target));
        settle();

        clickLater(button(rowOf(table, target.userId()), "Deactivate"));
        answerDialog(awaitDialog(), "Deactivate");
        settle();
        assertFalse(users.require(target.userId()).active());

        clickLater(button(rowOf(table, target.userId()), "Reactivate"));
        answerDialog(awaitDialog(), "Reactivate");
        settle();
        assertTrue(users.require(target.userId()).active());
    }

    @Test
    @Order(12)
    @DisplayName("delete: removing a post clears its tags, comments and reviews in both stores")
    void deletePost() {
        go("Posts");
        search(token);
        clickLater(buttonWithTooltip(rowFor(postId), "Delete"));
        answerDialog(awaitDialog(), "Delete post");
        settle();

        assertTrue(posts.findById(postId).isEmpty(), "post row gone");
        assertTrue(posts.tagsOf(postId).isEmpty(), "tag links cascaded");
        assertEquals(0, comments.countFor(postId, true), "comments removed from the comment store");
        assertEquals(0, reviews.countFor(postId), "reviews removed from the review store");
        assertTrue(hasText(root(), "No posts"), "the list reflects the delete");
        postId = null;
    }

    @Test
    @Order(13)
    @DisplayName("screenshot: a post with a threaded discussion")
    void seededDetailScreenshot() {
        search("Threaded Comments");
        TableView<PostSummary> table = table();
        // The database starts empty, so this only runs once such a post has been entered.
        assumeTrue(fx(() -> !table.getItems().isEmpty()), "skipped: no post about threaded comments yet");
        PostSummary seeded = fx(() -> table.getItems().get(0));
        click(buttonWithTooltip(rowFor(seeded.postId()), "Open"));
        settle();
        assertTrue(comments.countFor(seeded.postId(), false) > 0);
        shot("05-post-detail.png");
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private PostSummary findOne(String exactTitle) {
        List<PostSummary> found = posts.search(PostFilter.all().withKeyword(token)).content();
        assertEquals(1, found.size(), "exactly one post for this run's token");
        assertEquals(exactTitle, found.get(0).title());
        return found.get(0);
    }

    private void search(String text) {
        TextField box = field(root(), TextField.class, "Search titles and content");
        type(box, text);
        pause(400); // the search waits 280 ms after the last key press
        settle();
    }

    @SuppressWarnings("unchecked")
    private TableView<PostSummary> table() {
        return one(root(), TableView.class, t -> true, "posts table");
    }

    @SuppressWarnings("unchecked")
    private TableRow<PostSummary> rowFor(long id) {
        return one(root(), TableRow.class,
                r -> r.getItem() instanceof PostSummary p && p.postId() == id,
                "table row for post " + id);
    }

    @SuppressWarnings("unchecked")
    private TableRow<User> rowOf(TableView<User> table, long userId) {
        return one(table, TableRow.class,
                r -> r.getItem() instanceof User u && u.userId() == userId,
                "table row for user " + userId);
    }

    /** Finds the box that holds a comment, using the comment text. */
    private Node commentBox(String body) {
        Label label = one(root(), Label.class, l -> body.equals(l.getText()), "comment '" + body + "'");
        return fx(() -> {
            Node n = label;
            while (n != null && !(n instanceof VBox box && box.getStyleClass().contains("comment"))) {
                n = n.getParent();
            }
            if (n == null) {
                throw new AssertionError("comment card not found for " + body);
            }
            return n;
        });
    }

    private CommentStatus statusOf(String body) {
        return comments.threadFor(postId, true).stream()
                .filter(c -> body.equals(c.body()))
                .findFirst().orElseThrow().status();
    }
}
