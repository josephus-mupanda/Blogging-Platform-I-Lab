package org.example.bloggingplatformi.view;

import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * One post in full: body, threaded comments and reviews.
 *
 * <p>This is the screen where the polyglot design is visible at runtime. The
 * post comes from PostgreSQL; the comments and reviews come from whichever
 * store {@code app.comments.store} selects, and the panel header says which
 * one so a demo can switch between them and show the same UI working against
 * either engine.
 */
public class PostDetailView implements View {

    private static final int INDENT_PER_LEVEL = 26;
    private static final int MAX_VISUAL_DEPTH = 6;

    private final AppShell shell;
    private final long postId;

    private final PostService postService = new PostService();
    private final CommentService commentService = new CommentService();
    private final ReviewService reviewService = new ReviewService();

    private final VBox root = new VBox(16);
    private final VBox articleCard = new VBox(14);
    private final VBox commentList = new VBox(9);
    private final VBox reviewList = new VBox(9);
    private final VBox ratingSummary = new VBox(8);
    private final FlowPane tagRow = new FlowPane(7, 7);

    private final TextArea commentInput = new TextArea();
    private final Label commentStoreLabel = Styles.label("", "text-caption");
    private final Label reviewStoreLabel = Styles.label("", "text-caption");

    private Post post;

    public PostDetailView(AppShell shell, long postId) {
        this.shell = shell;
        this.postId = postId;

        buildLayout();
    }

    @Override
    public Node getRoot() {
        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        return scroller;
    }

    @Override
    public String title() {
        return post == null ? "Post" : post.title();
    }

    @Override
    public List<Node> actions() {
        Button edit = new Button("Edit");
        edit.setGraphic(Icons.of(Icons.EDIT, 15));
        edit.getStyleClass().addAll("btn-secondary", "btn-small");
        edit.setOnAction(event -> shell.push(new PostEditorView(shell, postId)));
        return List.of(edit);
    }

    @Override
    public void onShow() {
        loadPost();
        loadComments();
        loadReviews();

        // Fire-and-forget: a failed counter increment must not interrupt
        // someone reading, and the UPDATE is atomic in SQL so there is no
        // read-modify-write to race.
        Async.runQuietly(() -> postService.recordView(postId));
    }

    // -----------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------

    private void buildLayout() {
        articleCard.getStyleClass().add("card");

        VBox left = new VBox(16, articleCard, buildCommentsCard());
        HBox.setHgrow(left, Priority.ALWAYS);

        VBox right = new VBox(16, buildRatingCard(), buildReviewsCard());
        right.setMinWidth(260);
        right.setPrefWidth(320);
        right.setMaxWidth(340);

        root.getChildren().add(new HBox(16, left, right));
        root.setPadding(new Insets(24, 26, 28, 26));
    }

    private VBox buildCommentsCard() {
        commentInput.setPromptText("Add a comment…");
        commentInput.setWrapText(true);
        commentInput.setPrefRowCount(3);

        Button post = new Button("Comment");
        post.getStyleClass().addAll("btn-primary", "btn-small");
        post.setOnAction(event -> submitComment(null, commentInput, post));

        HBox composer = Styles.row(9, Styles.spacer(), post);

        VBox header = new VBox(3,
                Styles.subtitle("Discussion"),
                commentStoreLabel);
        header.getStyleClass().add("card-header");

        VBox body = new VBox(14, commentList, new VBox(7, commentInput, composer));
        body.setPadding(new Insets(14, 18, 16, 18));

        VBox card = new VBox(header, body);
        card.getStyleClass().add("card-flush");
        return card;
    }

    private VBox buildRatingCard() {
        VBox header = new VBox(Styles.subtitle("Ratings"));
        header.getStyleClass().add("card-header");

        VBox body = new VBox(ratingSummary);
        body.setPadding(new Insets(14, 18, 16, 18));

        VBox card = new VBox(header, body);
        card.getStyleClass().add("card-flush");
        return card;
    }

    private VBox buildReviewsCard() {
        VBox header = new VBox(3, Styles.subtitle("Reviews"), reviewStoreLabel);
        header.getStyleClass().add("card-header");

        VBox body = new VBox(12, reviewList, buildReviewComposer());
        body.setPadding(new Insets(14, 18, 16, 18));

        VBox card = new VBox(header, body);
        card.getStyleClass().add("card-flush");
        return card;
    }

    private Node buildReviewComposer() {
        ComboBox<Integer> rating = new ComboBox<>();
        rating.getItems().addAll(5, 4, 3, 2, 1);
        rating.setValue(5);
        rating.setPrefWidth(70);

        TextField headline = new TextField();
        headline.setPromptText("Headline (optional)");
        HBox.setHgrow(headline, Priority.ALWAYS);

        Button submit = new Button("Review");
        submit.getStyleClass().addAll("btn-secondary", "btn-small");
        submit.setOnAction(event -> submitReview(rating.getValue(), headline, submit));

        VBox composer = new VBox(7,
                Styles.label("Leave a review", "field-label"),
                Styles.row(7, rating, headline),
                Styles.row(7, Styles.spacer(), submit));
        composer.setPadding(new Insets(10, 0, 0, 0));
        return composer;
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
     * One comment, indented by its depth.
     *
     * <p>The thread arrives already ordered with parents before their replies,
     * from the recursive CTE on the SQL side or the path sort on the Mongo
     * side, so rendering is a flat loop rather than a tree walk. Indentation
     * is capped so a deep chain does not push text off the right edge.
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

        if (Session.canModerate()) {
            Button toggle = new Button(
                    comment.status() == CommentStatus.VISIBLE ? "Hide" : "Restore");
            toggle.getStyleClass().addAll("btn-ghost", "btn-small");
            toggle.setOnAction(event -> Async.run(
                    () -> comment.status() == CommentStatus.VISIBLE
                            ? commentService.hide(comment.commentId())
                            : commentService.restore(comment.commentId()),
                    updated -> loadComments()));

            Button delete = new Button("Delete");
            delete.getStyleClass().addAll("btn-ghost", "btn-small");
            delete.setOnAction(event -> confirmDeleteComment(comment));

            actions.getChildren().addAll(toggle, delete);
        }
        return actions;
    }

    /** Expands an inline composer under the comment being replied to. */
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
            Dialogs.error("No user selected",
                    "Choose a user in the top right before commenting.");
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
            Label stars = Styles.label(review.stars(), "text-mono");
            Label name = Styles.label(review.reviewerName(), "text-small");

            VBox box = new VBox(4, Styles.row(8, stars, name));
            if (review.headline() != null) {
                Label headline = Styles.label(review.headline(), "text-body");
                headline.setWrapText(true);
                box.getChildren().add(headline);
            }
            if (!review.criteria().isEmpty()) {
                // Only the document store can supply these; the JDBC DAO
                // returns an empty map, so the row simply does not appear.
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

        // Five fixed rows, including the ratings nobody gave: a distribution
        // with gaps silently removed is misleading.
        for (int stars = 5; stars >= 1; stars--) {
            long count = distribution.getOrDefault(stars, 0L);
            ratingSummary.getChildren().add(distributionRow(stars, count, total));
        }
    }

    private Node distributionRow(int stars, long count, long total) {
        Label label = Styles.label(stars + "★", "text-caption");
        label.setMinWidth(24);

        Region fill = new Region();
        fill.getStyleClass().add("rating-bar-fill");
        double ratio = total == 0 ? 0 : (double) count / total;
        fill.setPrefWidth(Math.max(2, ratio * 170));
        fill.setMinWidth(Math.max(2, ratio * 170));

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("rating-bar-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setPrefWidth(170);
        HBox.setHgrow(track, Priority.ALWAYS);

        Label countLabel = Styles.label(String.valueOf(count), "text-caption");
        countLabel.setMinWidth(24);
        countLabel.setAlignment(Pos.CENTER_RIGHT);

        return Styles.row(8, label, track, countLabel);
    }

    private void submitReview(Integer rating, TextField headline, Button trigger) {
        if (!Session.isSignedIn()) {
            Dialogs.error("No user selected",
                    "Choose a user in the top right before reviewing.");
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
