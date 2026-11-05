package org.example.bloggingplatformi.controller;

import java.util.ArrayList;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;

/** Controller of post-editor.fxml: create a new post (postId = null) or edit one. */
public class PostEditorController {

    private final MainController main;
    private final Long postId;
    private final PostService postService = new PostService();
    private final TagService tagService = new TagService();
    private final List<CheckBox> tagOptions = new ArrayList<>();
    private final Parent root;

    @FXML private TextField titleField;
    @FXML private TextArea bodyArea;
    @FXML private FlowPane tagChoices;
    @FXML private Label noTagsLabel;
    @FXML private Label titleError;
    @FXML private Label bodyError;

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
        titleError.visibleProperty().bind(titleError.textProperty().isNotEmpty());
        bodyError.visibleProperty().bind(bodyError.textProperty().isNotEmpty());
        titleField.textProperty().addListener((observable, before, after) -> titleError.setText(""));
        bodyArea.textProperty().addListener((observable, before, after) -> bodyError.setText(""));
        List<Tag> assignedTags = List.of();
        if (postId != null) {
            Post post = postService.findById(postId);
            titleField.setText(post.title());
            bodyArea.setText(post.body());

            assignedTags = postService.tagsOf(postId);
        }
        for (Tag tag : tagService.findAll()) {
            CheckBox option = new CheckBox(tag.name());
            option.getStyleClass().add("tag-option");
            option.setUserData(tag.id());
            option.setSelected(assignedTags.contains(tag));
            tagOptions.add(option);
        }
        tagChoices.getChildren().setAll(tagOptions);
        noTagsLabel.setVisible(tagOptions.isEmpty());
    }

    @FXML
    private void save() {
        try {
            List<Long> tagIds = tagOptions.stream()
                    .filter(CheckBox::isSelected)
                    .map(option -> (Long) option.getUserData())
                    .toList();
            if (postId == null) {
                long newId = postService.create(Session.user().id(), titleField.getText(), bodyArea.getText(), tagIds);
                main.showPost(newId);
            } else {
                postService.update(postId, titleField.getText(), bodyArea.getText(), tagIds);
                main.showPost(postId);
            }
        } catch (ValidationException e) {
            if ("title".equals(e.field())) {
                titleError.setText(e.getMessage());
                titleField.requestFocus();
            } else {
                bodyError.setText(e.getMessage());
                bodyArea.requestFocus();
            }
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
