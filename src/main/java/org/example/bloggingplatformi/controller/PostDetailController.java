package org.example.bloggingplatformi.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;

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
    @FXML private Label errorLabel;
    @FXML private ListView<Comment> commentList;
    @FXML private TextField commentField;
    @FXML private Button editCommentButton;
    @FXML private Button deleteCommentButton;
    @FXML private Label averageLabel;
    @FXML private ListView<Review> reviewList;
    @FXML private ComboBox<Integer> ratingBox;
    @FXML private TextField reviewField;

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
        ratingBox.getItems().addAll(5, 4, 3, 2, 1);

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
        List<String> tags = new ArrayList<>();
        for (Tag tag : postService.tagsOf(postId)) {
            tags.add(tag.name());
        }
        titleLabel.setText(post.title());
        infoLabel.setText("By " + post.authorName() + ", " + Dates.format(post.createdAt())
                + (tags.isEmpty() ? "" : "   Tags: " + String.join(", ", tags)));
        bodyLabel.setText(post.body());
        editButton.setVisible(Session.canEditPost(post.authorId()));
    }

    private void showComments() {
        commentList.getItems().setAll(commentService.forPost(postId));
    }

    private void showReviews() {
        List<Review> reviews = reviewService.forPost(postId);
        reviewList.getItems().setAll(reviews);
        averageLabel.setText(reviews.isEmpty()
                ? "No reviews yet."
                : String.format("Average: %.1f / 5 (%d reviews)", reviewService.average(reviews), reviews.size()));
    }

    @FXML
    private void addComment() {
        try {
            commentService.add(postId, Session.user(), commentField.getText());
            commentField.clear();
            errorLabel.setText("");
            showComments();
        } catch (ValidationException e) {
            errorLabel.setText(e.getMessage());
        } catch (RuntimeException e) {
            Dialogs.error("Could not save the comment. Is MongoDB running?\n\n" + e.getMessage());
        }
    }

    @FXML
    private void editComment() {
        Comment comment = commentList.getSelectionModel().getSelectedItem();
        TextInputDialog dialog = new TextInputDialog(comment.body());
        dialog.setHeaderText("Edit your comment");
        Optional<String> newText = dialog.showAndWait();
        if (newText.isPresent()) {
            try {
                commentService.edit(comment, Session.user(), newText.get());
                errorLabel.setText("");
                showComments();
            } catch (ValidationException e) {
                errorLabel.setText(e.getMessage());
            }
        }
    }

    @FXML
    private void deleteComment() {
        Comment comment = commentList.getSelectionModel().getSelectedItem();
        if (Dialogs.confirm("Delete this comment?")) {
            commentService.delete(comment, Session.user());
            showComments();
        }
    }

    @FXML
    private void addReview() {
        if (ratingBox.getValue() == null) {
            errorLabel.setText("Please choose a rating from 1 to 5.");
            return;
        }
        try {
            reviewService.add(postId, Session.user(), ratingBox.getValue(), reviewField.getText());
            reviewField.clear();
            errorLabel.setText("");
            showReviews();
        } catch (ValidationException e) {
            errorLabel.setText(e.getMessage());
        } catch (RuntimeException e) {
            Dialogs.error("Could not save the review. Is MongoDB running?\n\n" + e.getMessage());
        }
    }

    @FXML
    private void editPost() {
        main.showEditor(postId);
    }

    @FXML
    private void back() {
        main.showPosts();
    }
}
