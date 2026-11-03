package org.example.bloggingplatformi.controller;

import java.util.List;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.CommentStatus;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Review;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.CommentService;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.ReviewService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * Shows one post with its comments and reviews.
 * The post comes from PostgreSQL. Comments and reviews come from the store set in
 * {@code app.comments.store}, and the panel header shows which one.
 */
public class PostDetailController implements View {

    private static final int INDENT_PER_LEVEL = 26;
    private static final int MAX_VISUAL_DEPTH = 6;

    private final AppShell shell;
    private final long postId;

    private final PostService postService = new PostService();
    private final CommentService commentService = new CommentService();
    private final ReviewService reviewService = new ReviewService();

    /** The post's tags, shown inside the article card once the post has loaded. */
    private final FlowPane tagRow = new FlowPane(7, 7);
    private final Node root;

    // Filled by FXMLLoader from the fx:id values in post-detail.fxml.
    @FXML private VBox articleCard;
    @FXML private Label commentStoreLabel;
    @FXML private VBox commentList;
    @FXML private TextArea commentInput;
    @FXML private Button commentButton;
    @FXML private VBox ratingSummary;
    @FXML private Label reviewStoreLabel;
    @FXML private VBox reviewList;
    @FXML private ComboBox<Integer> ratingBox;
    @FXML private TextField reviewHeadline;
    @FXML private Button reviewButton;

    private Post post;

    public PostDetailController(AppShell shell, long postId) {
        this.shell = shell;
        this.postId = postId;
        this.root = Fxml.load("post-detail.fxml", this);
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public String title() {
        return post == null ? "Post" : post.title();
    }

    @Override
    public List<Node> actions() {
        // Only the post's author and the admin get the Edit button.
        // Until the post has loaded we do not know the author, so no button is shown yet.
        if (post == null || !Session.canEditPost(post.authorId())) {
            return List.of();
        }
        Button edit = new Button("Edit");
        edit.setGraphic(Icons.of(Icons.EDIT, 15));
        edit.getStyleClass().addAll("btn-secondary", "btn-small");
        edit.setOnAction(event -> shell.push(new PostEditorController(shell, postId)));
        return List.of(edit);
    }

    @Override
    public void onShow() {
        loadPost();
        loadComments();
        loadReviews();

        // Count the view in the background. If it fails, the user is not told.
        Async.runQuietly(() -> postService.recordView(postId));
    }

    // -----------------------------------------------------------------
    // Buttons from the FXML (onAction="#...")
    // -----------------------------------------------------------------

    /** The "Comment" button: a new top-level comment (no parent). */
    @FXML
    private void postComment() {
        submitComment(null, commentInput, commentButton);
    }

    /** The "Review" button. */
    @FXML
    private void postReview() {
        submitReview(ratingBox.getValue(), reviewHeadline, reviewButton);
    }

    // -----------------------------------------------------------------
    // Post
    // -----------------------------------------------------------------

    private void loadPost() {
        articleCard.getChildren().setAll(Styles.label("Loading…", "text-small"));

        Async.run(() -> Map.entry(postService.require(postId), postService.tagsOf(postId)),
                entry -> {
                    post = entry.getKey();
                    renderArticle(entry.getValue());
                    shell.refreshHeading(this);
                },
                error -> {
                    articleCard.getChildren().setAll(
                            Styles.label("This post could not be loaded.", "text-subtitle"),
                            Styles.message(Dialogs.humanise(error)));
                });
    }

    private void renderArticle(List<Tag> tags) {
        Label heading = Styles.label(post.title(), "text-display");
        heading.setWrapText(true);

        Label meta = Styles.label(
                Styles.formatDate(post.publishedAt() != null ? post.publishedAt() : post.createdAt())
                        + "   ·   " + Styles.number(post.viewCount()) + " views",
                "text-small");

        HBox metaRow = Styles.row(10, Styles.statusBadge(post.status()), meta);

        tagRow.getChildren().clear();
        tags.forEach(tag -> tagRow.getChildren().add(Styles.chip(tag.name())));

        Label body = Styles.label(post.body(), "text-body");
        body.setWrapText(true);
        body.setStyle("-fx-line-spacing: 4px;");

        articleCard.getChildren().setAll(heading, metaRow, separator(), body);
        if (!tags.isEmpty()) {
            articleCard.getChildren().add(tagRow);
        }
    }

    private Region separator() {
        Region line = new Region();
        line.setStyle("-fx-background-color: -color-border;");
        line.setMinHeight(1);
        line.setPrefHeight(1);
        line.setMaxHeight(1);
        return line;
    }

    // -----------------------------------------------------------------
    // Comments
    // -----------------------------------------------------------------

    private void loadComments() {
        commentStoreLabel.setText("Loading…");
        commentList.getChildren().setAll(Styles.label("Loading…", "text-small"));

        boolean includeHidden = Session.canModerate();

        Async.run(() -> Map.entry(commentService.threadFor(postId, includeHidden),
                                  commentService.activeStore()),
                entry -> {
                    List<Comment> thread = entry.getKey();
                    commentStoreLabel.setText(thread.size() + " comments  ·  stored in "
                            + entry.getValue());

                    if (thread.isEmpty()) {
                        commentList.getChildren().setAll(
                                Styles.message("No comments yet. Start the discussion."));
                        return;
                    }
                    commentList.getChildren().clear();
                    thread.forEach(comment -> commentList.getChildren().add(renderComment(comment)));
                },
                error -> {
                    commentStoreLabel.setText("unavailable");
                    commentList.getChildren().setAll(Styles.message(Dialogs.humanise(error)));
                });
    }

    /**
     * Draws one comment, indented by its depth.
     * The list already has each parent before its replies, so a simple loop is enough.
     * The indent stops growing after {@code MAX_VISUAL_DEPTH} levels.
     */
    private Node renderComment(Comment comment) {
        Label author = Styles.label(comment.displayName(), "text-body");
        author.setStyle("-fx-font-weight: 600;");

        Label when = Styles.label(Styles.relative(comment.createdAt()), "text-caption");

        HBox header = Styles.row(8, Styles.avatar(comment.displayName(), true), author, when);
        if (comment.status() != CommentStatus.VISIBLE) {
            header.getChildren().add(Styles.statusBadge(comment.status()));
        }

        Label body = Styles.label(comment.body(), "comment-body");
        body.setWrapText(true);
        body.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(7, header, body, buildCommentActions(comment));
        box.getStyleClass().add("comment");
        if (!comment.isRoot()) {
            box.getStyleClass().add("comment-reply");
        }
        if (comment.status() != CommentStatus.VISIBLE) {
            box.getStyleClass().add("comment-hidden");
        }
        HBox.setHgrow(box, Priority.ALWAYS);

        double indent = Math.min(comment.depth(), MAX_VISUAL_DEPTH) * INDENT_PER_LEVEL;
        HBox indented = new HBox(box);
        indented.setPadding(new Insets(0, 0, 0, indent));
        return indented;
    }

    private Node buildCommentActions(Comment comment) {
        Button reply = new Button("Reply");
        reply.getStyleClass().addAll("btn-ghost", "btn-small");
        reply.setOnAction(event -> openReplyBox(comment, reply));

        HBox actions = Styles.row(4, reply);

        // The author of a comment can edit or delete it; an admin can too.
        boolean isOwnComment = comment.authorId() != null
                && comment.authorId() == Session.currentUserId();
        boolean canChange = isOwnComment || Session.canModerate();

        if (canChange) {
            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-ghost", "btn-small");
            edit.setOnAction(event -> openEditBox(comment, edit));
            actions.getChildren().add(edit);
        }

        if (Session.canModerate()) {
            Button toggle = new Button(
                    comment.status() == CommentStatus.VISIBLE ? "Hide" : "Restore");
            toggle.getStyleClass().addAll("btn-ghost", "btn-small");
            toggle.setOnAction(event -> Async.run(
                    () -> comment.status() == CommentStatus.VISIBLE
                            ? commentService.hide(comment.commentId())
                            : commentService.restore(comment.commentId()),
                    updated -> loadComments()));
            actions.getChildren().add(toggle);
        }

        if (canChange) {
            Button delete = new Button("Delete");
            delete.getStyleClass().addAll("btn-ghost", "btn-small");
            delete.setOnAction(event -> confirmDeleteComment(comment));
            actions.getChildren().add(delete);
        }
        return actions;
    }

    /** Opens a text box under the comment, filled with its current text. */
    private void openEditBox(Comment comment, Button trigger) {
        TextArea input = new TextArea(comment.body());
        input.setWrapText(true);
        input.setPrefRowCount(2);

        Button save = new Button("Save");
        save.getStyleClass().addAll("btn-primary", "btn-small");

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn-ghost", "btn-small");

        VBox editor = new VBox(7, input, Styles.row(7, Styles.spacer(), cancel, save));
        editor.setPadding(new Insets(8, 0, 0, 0));

        VBox container = (VBox) trigger.getParent().getParent();
        container.getChildren().add(editor);
        trigger.setDisable(true);
        input.requestFocus();

        cancel.setOnAction(event -> {
            container.getChildren().remove(editor);
            trigger.setDisable(false);
        });
        save.setOnAction(event -> {
            save.setDisable(true);
            Async.run(() -> commentService.editBody(comment.commentId(), input.getText()),
                    updated -> loadComments(),
                    error -> {
                        save.setDisable(false);
                        Dialogs.error("Could not save the comment", error);
                    });
        });
    }

    /** Opens a reply box under the comment being replied to. */
    private void openReplyBox(Comment parent, Button trigger) {
        TextArea input = new TextArea();
        input.setPromptText("Replying to " + parent.displayName() + "…");
        input.setWrapText(true);
        input.setPrefRowCount(2);

        Button send = new Button("Post reply");
        send.getStyleClass().addAll("btn-primary", "btn-small");

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn-ghost", "btn-small");

        VBox composer = new VBox(7, input, Styles.row(7, Styles.spacer(), cancel, send));
        composer.setPadding(new Insets(8, 0, 0, 0));

        VBox container = (VBox) trigger.getParent().getParent();
        container.getChildren().add(composer);
        trigger.setDisable(true);
        input.requestFocus();

        cancel.setOnAction(event -> {
            container.getChildren().remove(composer);
            trigger.setDisable(false);
        });
        send.setOnAction(event -> submitComment(parent, input, send));
    }

    private void submitComment(Comment parent, TextArea input, Button trigger) {
        if (!Session.isSignedIn()) {
            Dialogs.error("Not signed in",
                    "Sign in before commenting.");
            return;
        }
        String body = input.getText();
        if (body == null || body.isBlank()) {
            return;
        }

        long authorId = Session.currentUserId();
        String authorName = Session.currentDisplayName();

        trigger.setDisable(true);
        Async.run(() -> parent == null
                        ? commentService.addComment(postId, authorId, authorName, body)
                        : commentService.addReply(parent.commentId(), authorId, authorName, body),
                saved -> {
                    input.clear();
                    trigger.setDisable(false);
                    loadComments();
                },
                error -> {
                    trigger.setDisable(false);
                    Dialogs.error("Could not post the comment", error);
                });
    }

    private void confirmDeleteComment(Comment comment) {
        boolean confirmed = Dialogs.confirm("Delete comment",
                "Delete this comment and every reply beneath it?\n\nThis cannot be undone.",
                "Delete");
        if (confirmed) {
            Async.run(() -> commentService.delete(comment.commentId()),
                    removed -> loadComments());
        }
    }

    // -----------------------------------------------------------------
    // Reviews
    // -----------------------------------------------------------------

    private void loadReviews() {
        reviewStoreLabel.setText("Loading…");

        Async.run(() -> Map.entry(reviewService.findForPost(postId),
                                  reviewService.ratingDistribution(postId)),
                entry -> {
                    renderReviews(entry.getKey());
                    renderDistribution(entry.getValue(), entry.getKey().size());
                    reviewStoreLabel.setText("stored in " + reviewService.activeStore());
                },
                error -> {
                    reviewStoreLabel.setText("unavailable");
                    reviewList.getChildren().setAll(Styles.message(Dialogs.humanise(error)));
                    ratingSummary.getChildren().setAll(
                            Styles.label("—", "text-metric"));
                });
    }

    private void renderReviews(List<Review> reviews) {
        if (reviews.isEmpty()) {
            reviewList.getChildren().setAll(
                    Styles.label("No reviews yet.", "text-small"));
            return;
        }
        reviewList.getChildren().clear();
        for (Review review : reviews.stream().limit(6).toList()) {
            Label stars = Styles.label(review.stars(), "review-stars");
            Label name = Styles.label(review.reviewerName(), "text-small");

            VBox box = new VBox(4, Styles.row(8, stars, name));
            if (review.headline() != null) {
                Label headline = Styles.label(review.headline(), "text-body");
                headline.setWrapText(true);
                box.getChildren().add(headline);
            }
            if (!review.criteria().isEmpty()) {
                // Only MongoDB stores criteria. The JDBC DAO returns an empty map, so this row is skipped.
                String detail = review.criteria().entrySet().stream()
                        .map(e -> e.getKey() + " " + e.getValue())
                        .reduce((a, b) -> a + "  ·  " + b)
                        .orElse("");
                box.getChildren().add(Styles.label(detail, "text-caption"));
            }
            box.getStyleClass().add("comment");
            reviewList.getChildren().add(box);
        }
    }

    private void renderDistribution(Map<Integer, Long> distribution, int totalReviews) {
        ratingSummary.getChildren().clear();

        if (distribution.isEmpty()) {
            ratingSummary.getChildren().addAll(
                    Styles.label("—", "text-metric"),
                    Styles.label("No ratings yet", "text-small"));
            return;
        }

        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        double weighted = distribution.entrySet().stream()
                .mapToDouble(entry -> entry.getKey() * entry.getValue())
                .sum();
        double average = total == 0 ? 0 : weighted / total;

        Label averageLabel = Styles.label(String.format("%.2f", average), "text-metric");
        Label countLabel = Styles.label("from " + Styles.number(total)
                + (total == 1 ? " review" : " reviews"), "text-small");

        ratingSummary.getChildren().addAll(averageLabel, countLabel);

        // Always show all five rows, even for ratings with no reviews.
        for (int stars = 5; stars >= 1; stars--) {
            long count = distribution.getOrDefault(stars, 0L);
            ratingSummary.getChildren().add(distributionRow(stars, count, total));
        }
    }

    private Node distributionRow(int stars, long count, long total) {
        Label label = Styles.label(stars + "★", "text-caption");
        label.setMinWidth(24);

        Node track = Styles.proportionBar(total == 0 ? 0 : (double) count / total, 170);

        Label countLabel = Styles.label(String.valueOf(count), "text-caption");
        countLabel.setMinWidth(24);
        countLabel.setAlignment(Pos.CENTER_RIGHT);

        return Styles.row(8, label, track, countLabel);
    }

    private void submitReview(Integer rating, TextField headline, Button trigger) {
        if (!Session.isSignedIn()) {
            Dialogs.error("Not signed in",
                    "Sign in before reviewing.");
            return;
        }
        long reviewerId = Session.currentUserId();
        String reviewerName = Session.currentDisplayName();
        String headlineText = headline.getText();

        trigger.setDisable(true);
        Async.run(() -> reviewService.add(postId, reviewerId, reviewerName,
                        rating == null ? 5 : rating, headlineText, null),
                saved -> {
                    headline.clear();
                    trigger.setDisable(false);
                    loadReviews();
                },
                error -> {
                    trigger.setDisable(false);
                    Dialogs.error("Could not save the review", error);
                });
    }
}
