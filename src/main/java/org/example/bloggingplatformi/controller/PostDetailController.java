package org.example.bloggingplatformi.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Review;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.CommentService;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.ReviewService;
import org.example.bloggingplatformi.ui.Dates;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;

/** Controller of post-detail.fxml: one post with its comments and reviews. */
public class PostDetailController {

    private final MainController main;
    private final long postId;
    private final PostService postService = new PostService();
    private final CommentService commentService = new CommentService();
    private final ReviewService reviewService = new ReviewService();
    private final Parent root;

    @FXML private Label titleLabel;
    @FXML private Label infoLabel;
    @FXML private Label bodyLabel;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private FlowPane postTags;
    @FXML private Label commentError;
    @FXML private Label reviewError;
    @FXML private ListView<Comment> commentList;
    @FXML private TextArea commentField;
    @FXML private Button editCommentButton;
    @FXML private Button deleteCommentButton;
    @FXML private Label averageLabel;
    @FXML private ListView<Review> reviewList;
    @FXML private FlowPane ratingChoices;
    @FXML private TextArea reviewField;
    private final ToggleGroup ratings = new ToggleGroup();

    public PostDetailController(MainController main, long postId) {
        this.main = main;
        this.postId = postId;
        this.root = Fxml.load("post-detail.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        commentError.visibleProperty().bind(commentError.textProperty().isNotEmpty());
        reviewError.visibleProperty().bind(reviewError.textProperty().isNotEmpty());
        for (int score = 1; score <= 5; score++) {
            ToggleButton option = new ToggleButton(score + " ★");
            option.getStyleClass().add("rating-option");
            option.setAccessibleText(score + " out of 5 stars");
            option.setUserData(score);
            option.setToggleGroup(ratings);
            ratingChoices.getChildren().add(option);
        }

        commentList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Comment comment, boolean empty) {
                super.updateItem(comment, empty);
                setGraphic(empty ? null : entry(this, comment.authorName(), comment.createdAt(), comment.body(), null));
            }
        });
        reviewList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Review review, boolean empty) {
                super.updateItem(review, empty);
                setGraphic(empty ? null : entry(this, review.reviewerName(), review.createdAt(), review.text(), review.rating()));
            }
        });

        // The comment buttons work only on your own comment (or for the admin).
        commentList.getSelectionModel().selectedItemProperty().addListener((observable, oldComment, comment) -> {
            boolean canChange = comment != null && commentService.canChange(comment, Session.user());
            editCommentButton.setDisable(!canChange);
            deleteCommentButton.setDisable(!canChange);
        });
        editCommentButton.setDisable(true);
        deleteCommentButton.setDisable(true);

        try {
            showPost();
            showComments();
            showReviews();
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the post: " + e.getMessage());
        }
    }

    private void showPost() {
        Post post = postService.findById(postId); // from the cache if it is there
        for (Tag tag : postService.tagsOf(postId)) {
            Label chip = new Label(tag.name());
            chip.getStyleClass().add("tag-chip");
            postTags.getChildren().add(chip);
        }
        postTags.setVisible(!postTags.getChildren().isEmpty());
        titleLabel.setText(post.title());
        infoLabel.setText(post.authorName() + "  ·  " + Dates.format(post.createdAt()));
        bodyLabel.setText(post.body());
        boolean canEdit = Session.canEditPost(post.authorId());
        editButton.setVisible(canEdit);
        deleteButton.setVisible(canEdit);
    }

    private VBox entry(ListCell<?> cell, String name, LocalDateTime createdAt, String text, Integer rating) {
        Label author = new Label(name);
        author.getStyleClass().add("field-label");
        author.setWrapText(true);
        Label date = new Label(Dates.format(createdAt));
        date.getStyleClass().add("small");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox byline = new HBox(8, author, spacer, date);
        VBox entry = new VBox(6, byline);
        entry.prefWidthProperty().bind(cell.widthProperty().subtract(24));
        entry.setMinWidth(0);
        if (rating != null) {
            int filled = Math.max(0, Math.min(5, rating));
            Label stars = new Label("★".repeat(filled) + "☆".repeat(5 - filled) + "  " + rating + "/5");
            stars.getStyleClass().add("rating");
            entry.getChildren().add(stars);
        }
        if (text != null && !text.isBlank()) {
            Label body = new Label(text);
            body.setWrapText(true);
            entry.getChildren().add(body);
        }
        return entry;
    }

    private void showComments() {
        commentList.getItems().setAll(commentService.forPost(postId));
        commentList.setPrefHeight(commentList.getItems().isEmpty() ? 65 : 150);
    }

    private void showReviews() {
        List<Review> reviews = reviewService.forPost(postId);
        reviewList.getItems().setAll(reviews);
        reviewList.setPrefHeight(reviews.isEmpty() ? 65 : 150);
        averageLabel.setText(reviews.isEmpty()
                ? "No reviews yet."
                : String.format("%.1f / 5  ·  %d %s", reviewService.average(reviews), reviews.size(),
                        reviews.size() == 1 ? "review" : "reviews"));
    }

    @FXML
    private void addComment() {
        try {
            commentService.add(postId, Session.user(), commentField.getText());
            commentField.clear();
            commentError.setText("");
            showComments();
        } catch (ValidationException e) {
            commentError.setText(e.getMessage());
        } catch (RuntimeException e) {
            Dialogs.error("Could not save the comment. Is MongoDB running?\n\n" + e.getMessage());
        }
    }

    @FXML
    private void editComment() {
        Comment comment = commentList.getSelectionModel().getSelectedItem();
        Optional<String> newText = Dialogs.editComment(comment.body());
        if (newText.isPresent()) {
            try {
                commentService.edit(comment, Session.user(), newText.get());
                commentError.setText("");
                showComments();
            } catch (ValidationException e) {
                commentError.setText(e.getMessage());
            } catch (RuntimeException e) {
                Dialogs.error("Could not edit the comment: " + e.getMessage());
            }
        }
    }

    @FXML
    private void deleteComment() {
        Comment comment = commentList.getSelectionModel().getSelectedItem();
        if (Dialogs.confirm("Delete comment?", "This comment will be permanently removed.", "Delete comment", true)) {
            try {
                commentService.delete(comment, Session.user());
                commentError.setText("");
                showComments();
            } catch (RuntimeException e) {
                Dialogs.error("Could not delete the comment: " + e.getMessage());
            }
        }
    }

    @FXML
    private void addReview() {
        Toggle selected = ratings.getSelectedToggle();
        if (selected == null) {
            reviewError.setText("Choose a rating from 1 to 5.");
            return;
        }
        try {
            reviewService.add(postId, Session.user(), (Integer) selected.getUserData(), reviewField.getText());
            ratings.selectToggle(null);
            reviewField.clear();
            reviewError.setText("");
            showReviews();
        } catch (ValidationException e) {
            reviewError.setText(e.getMessage());
        } catch (RuntimeException e) {
            Dialogs.error("Could not save the review. Is MongoDB running?\n\n" + e.getMessage());
        }
    }

    @FXML
    private void editPost() {
        main.showEditor(postId);
    }

    @FXML
    private void deletePost() {
        if (Dialogs.confirm("Delete post?", "\"" + titleLabel.getText()
                + "\" and its comments and reviews will be permanently removed.", "Delete post", true)) {
            try {
                postService.delete(postId);
                main.showPosts();
            } catch (RuntimeException e) {
                Dialogs.error("Could not delete the post: " + e.getMessage());
            }
        }
    }

    @FXML
    private void back() {
        main.showPosts();
    }
}
