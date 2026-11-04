package org.example.bloggingplatformi.controller;

import java.util.ArrayList;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;

/** Controller of post-editor.fxml: create a new post (postId = null) or edit one. */
public class PostEditorController {

    private final MainController main;
    private final Long postId;
    private final PostService postService = new PostService();
    private final Parent root;

    @FXML private TextField titleField;
    @FXML private TextArea bodyArea;
    @FXML private TextField tagsField;
    @FXML private Label errorLabel;

    public PostEditorController(MainController main, Long postId) {
        this.main = main;
        this.postId = postId;
        this.root = Fxml.load("post-editor.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    /** When editing, fill the form with the post. */
    @FXML
    private void initialize() {
        if (postId != null) {
            Post post = postService.findById(postId);
            titleField.setText(post.title());
            bodyArea.setText(post.body());

            List<String> names = new ArrayList<>();
            for (Tag tag : postService.tagsOf(postId)) {
                names.add(tag.name());
            }
            tagsField.setText(String.join(", ", names));
        }
    }

    @FXML
    private void save() {
        try {
            if (postId == null) {
                long newId = postService.create(Session.user().id(), titleField.getText(), bodyArea.getText(), tagsField.getText());
                main.showPost(newId);
            } else {
                postService.update(postId, titleField.getText(), bodyArea.getText(), tagsField.getText());
                main.showPost(postId);
            }
        } catch (ValidationException e) {
            errorLabel.setText(e.getMessage()); // wrong input: show it under the form
        } catch (RuntimeException e) {
            Dialogs.error("Could not save the post: " + e.getMessage());
        }
    }

    @FXML
    private void cancel() {
        if (postId == null) {
            main.showPosts();
        } else {
            main.showPost(postId);
        }
    }
}
