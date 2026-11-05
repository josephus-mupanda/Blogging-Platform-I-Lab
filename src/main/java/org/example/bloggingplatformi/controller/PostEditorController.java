package org.example.bloggingplatformi.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.TagChips;

/** Controller of post-editor.fxml: create a new post (postId = null) or edit one. */
public class PostEditorController {

    private final MainController main;
    private final Long postId;
    private final PostService postService = new PostService();
    private final TagService tagService = new TagService();
    private final UserService userService = new UserService();
    private final Set<Tag> selectedTags = new LinkedHashSet<>();
    private final Parent root;
    private List<Tag> availableTags;
    private Draft initialDraft;
    private boolean updatingTagPicker;

    @FXML private TextField titleField;
    @FXML private TextArea bodyArea;
    @FXML private ComboBox<Tag> tagPicker;
    @FXML private FlowPane selectedTagsPane;
    @FXML private Label tagStatus;
    @FXML private Label tagError;
    @FXML private Label titleError;
    @FXML private Label bodyError;

    private record Draft(String title, String body, Set<Tag> tags) {}

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
        tagError.visibleProperty().bind(tagError.textProperty().isNotEmpty());
        tagStatus.visibleProperty().bind(tagStatus.textProperty().isNotEmpty());
        tagPicker.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Tag tag, boolean empty) {
                super.updateItem(tag, empty);
                setText(tagPicker.getPromptText());
            }
        });
        if (postId != null) {
            Post post = postService.findById(postId);
            titleField.setText(post.title());
            bodyArea.setText(post.body());

            selectedTags.addAll(postService.tagsOf(postId));
        }
        reloadTags();
        initialDraft = draft();
    }

    private void reloadTags() {
        availableTags = tagService.findAll();
        selectedTags.retainAll(availableTags);
        renderTags();
    }

    private void renderTags() {
        updatingTagPicker = true;
        try {
            List<Tag> options = availableTags.stream().filter(tag -> !selectedTags.contains(tag)).toList();
            tagPicker.getSelectionModel().clearSelection();
            tagPicker.setValue(null);
            tagPicker.getItems().setAll(options);
            tagPicker.setDisable(options.isEmpty());
            tagStatus.setText(availableTags.isEmpty() ? "No tags are available yet."
                    : options.isEmpty() ? "All available tags are selected." : "");
        } finally {
            updatingTagPicker = false;
        }
        selectedTagsPane.getChildren().setAll(selectedTags.stream().map(tag ->
                TagChips.removable(new Label(tag.name()), "Remove " + tag.name() + " from this post", () -> {
                    selectedTags.remove(tag);
                    renderTags();
                    tagPicker.requestFocus();
                })).toList());
        selectedTagsPane.setVisible(!selectedTags.isEmpty());
    }

    @FXML
    private void addSelectedTag() {
        Tag tag = tagPicker.getValue();
        if (!updatingTagPicker && tag != null && selectedTags.add(tag)) {
            renderTags();
        }
    }

    private Draft draft() {
        return new Draft(titleField.getText(), bodyArea.getText(), Set.copyOf(selectedTags));
    }

    boolean hasChanges() {
        return !initialDraft.equals(draft());
    }

    @FXML
    private void save() {
        try {
            userService.requireActive(Session.user());
            List<Long> tagIds = selectedTags.stream().map(Tag::id).toList();
            long savedId;
            if (postId == null) {
                savedId = postService.create(Session.user().id(), titleField.getText(), bodyArea.getText(), tagIds);
            } else {
                postService.update(postId, titleField.getText(), bodyArea.getText(), tagIds);
                savedId = postId;
            }
            initialDraft = draft();
            main.showPost(savedId);
        } catch (ValidationException e) {
            if ("title".equals(e.field())) {
                titleError.setText(e.getMessage());
                titleField.requestFocus();
            } else if ("body".equals(e.field())) {
                bodyError.setText(e.getMessage());
                bodyArea.requestFocus();
            } else if ("tags".equals(e.field())) {
                tagError.setText(e.getMessage());
                try {
                    reloadTags();
                } catch (RuntimeException reloadError) {
                    Dialogs.error("Could not refresh the tags: " + reloadError.getMessage());
                }
                tagPicker.requestFocus();
            } else {
                Dialogs.error(e.getMessage());
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
