package org.example.bloggingplatformi.controller;

import java.util.Map;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.service.CommentService;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.ReviewService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;

/** Controller of dashboard.fxml: counts from PostgreSQL and MongoDB, latest posts, most used tags. */
public class DashboardController {

    private final MainController main;
    private final PostService postService = new PostService();
    private final CommentService commentService = new CommentService();
    private final ReviewService reviewService = new ReviewService();
    private final UserService userService = new UserService();
    private final TagService tagService = new TagService();
    private final Parent root;

    @FXML private Label postsCount;
    @FXML private Label commentsCount;
    @FXML private Label reviewsCount;
    @FXML private Label usersCount;
    @FXML private Label tagsCount;
    @FXML private VBox latestPosts;
    @FXML private ListView<String> topTags;

    public DashboardController(MainController main) {
        this.main = main;
        this.root = Fxml.load("dashboard.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        try {
            // PostgreSQL
            postsCount.setText(String.valueOf(postService.count()));
            usersCount.setText(String.valueOf(userService.count()));
            tagsCount.setText(String.valueOf(tagService.count()));
            var posts = postService.latest(10);
            for (Post post : posts) {
                latestPosts.getChildren().add(main.postCard(post));
            }
            if (posts.isEmpty()) {
                Label empty = new Label("No posts yet.");
                empty.getStyleClass().add("small");
                latestPosts.getChildren().add(empty);
            }
            for (Map.Entry<String, Integer> tag : tagService.usage().entrySet()) {
                int count = tag.getValue();
                topTags.getItems().add(tag.getKey() + "  ·  " + count + (count == 1 ? " post" : " posts"));
            }
            // MongoDB
            commentsCount.setText(String.valueOf(commentService.count()));
            reviewsCount.setText(String.valueOf(reviewService.count()));
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the dashboard. Are PostgreSQL and MongoDB running?\n\n" + e.getMessage());
        }
    }
}
