package org.example.bloggingplatformi.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;
import org.example.bloggingplatformi.util.Excerpts;

/**
 * Controller for post-editor.fxml: the form for creating or editing a post.
 * If {@code postId} is null it creates a new post, otherwise it loads and updates that post.
 * Validation errors are shown under the field they belong to.
 */
public class PostEditorController implements View {

    private final AppShell shell;
    private final Long postId;

    private final PostService postService = new PostService();
    private final TagService tagService = new TagService();

    private final Node root;

    // Filled by FXMLLoader from the fx:id values in post-editor.fxml.
    @FXML private TextField titleField;
    @FXML private Label titleError;
    @FXML private TextArea bodyArea;
    @FXML private Label bodyError;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<String> tagInput;
    @FXML private FlowPane tagChips;
    @FXML private Label slugLabel;
    @FXML private Label excerptPreview;
    @FXML private Label metaLabel;

    private final Set<String> selectedTags = new LinkedHashSet<>();
    private final Button saveButton = new Button("Save post");

    private Post loaded;

    /** @param postId null for a new post, or the id of the post to edit */
    public PostEditorController(AppShell shell, Long postId) {
        this.shell = shell;
        this.postId = postId;
        this.root = Fxml.load("post-editor.fxml", this);
    }

    /** Called by FXMLLoader once the @FXML fields are filled. */
    @FXML
    private void initialize() {
        // Typing in a field removes its error message.
        titleField.textProperty().addListener((observable, old, text) -> clearError(titleField, titleError));
        bodyArea.textProperty().addListener((observable, old, text) -> {
            clearError(bodyArea, bodyError);
            // Show a preview of the excerpt that will be saved with the post.
            excerptPreview.setText(Excerpts.from(text, 160));
        });

        // Pressing Enter adds the typed tag, even if it is new. The service creates new tags on save.
        tagInput.getEditor().setOnAction(event -> addTag(tagInput.getEditor().getText()));
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public String title() {
        return postId == null ? "New post" : "Edit post";
    }

    @Override
    public String subtitle() {
        return postId == null
                ? "Drafts stay private until you publish them"
                : "Changes keep the original URL slug";
    }

    @Override
    public List<Node> actions() {
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn-ghost", "btn-small");
        cancel.setOnAction(event -> shell.back());

        saveButton.setGraphic(Icons.of(Icons.PLUS, 15));
        saveButton.getStyleClass().add("btn-primary");
        saveButton.setOnAction(event -> save());

        return List.of(cancel, saveButton);
    }

    @Override
    public void onShow() {
        loadTagSuggestions();
        if (postId != null) {
            loadPost();
        }
        titleField.requestFocus();
    }

    // -----------------------------------------------------------------
    // Tags
    // -----------------------------------------------------------------

    /** A tag was chosen in the dropdown list (onAction="#tagPicked" in the FXML). */
    @FXML
    private void tagPicked() {
        String value = tagInput.getValue();
        if (value != null && !value.isBlank()) {
            addTag(value);
        }
    }

    private void addTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String normalized = raw.strip().toLowerCase(Locale.ENGLISH);
        if (selectedTags.add(normalized)) {
            renderChips();
        }
        // Clear the box a moment later. Clearing it right away, while the
        // dropdown list is still handling the mouse click, crashes JavaFX.
        Platform.runLater(() -> {
            tagInput.getEditor().clear();
            tagInput.setValue(null);
        });
    }

    private void renderChips() {
        tagChips.getChildren().clear();
        for (String tag : selectedTags) {
            Label text = Styles.label(tag, "text-small");

            Button remove = new Button("×");
            remove.getStyleClass().add("chip-remove");
            remove.setOnAction(event -> {
                selectedTags.remove(tag);
                renderChips();
            });

            HBox chip = new HBox(2, text, remove);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("chip-removable");
            tagChips.getChildren().add(chip);
        }
    }

    private void loadTagSuggestions() {
        Async.run(tagService::findAll,
                tags -> tagInput.getItems().setAll(tags.stream().map(Tag::name).toList()),
                error -> { /* no suggestions, but typing a tag still works */ });
    }

    // -----------------------------------------------------------------
    // Load and save
    // -----------------------------------------------------------------

    private void loadPost() {
        Async.run(() -> {
            Post post = postService.require(postId);
            List<Tag> tags = postService.tagsOf(postId);
            return Map.entry(post, tags);
        }, entry -> {
            loaded = entry.getKey();
            titleField.setText(loaded.title());
            bodyArea.setText(loaded.body());
            statusBox.setValue(switch (loaded.status()) {
                case PUBLISHED -> "Published";
                case ARCHIVED -> "Archived";
                case DRAFT -> "Draft";
            });
            slugLabel.setText(loaded.slug());

            selectedTags.clear();
            entry.getValue().forEach(tag -> selectedTags.add(tag.name()));
            renderChips();

            metaLabel.setText("Created " + Styles.formatDateTime(loaded.createdAt())
                    + "\nLast updated " + Styles.formatDateTime(loaded.updatedAt())
                    + "\n" + Styles.number(loaded.viewCount()) + " views");
        }, error -> Dialogs.error("Could not load the post", error));
    }

    private void save() {
        clearError(titleField, titleError);
        clearError(bodyArea, bodyError);

        if (!Session.isSignedIn()) {
            Dialogs.error("Not signed in",
                    "Sign in before creating content.");
            return;
        }
        if (!Session.canPublish()) {
            Dialogs.error("Readers cannot write posts",
                    "Only bloggers (authors) and admins can save a post. Sign in with a blogger account.");
            return;
        }
        if (loaded != null && !Session.canEditPost(loaded.authorId())) {
            Dialogs.error("Not your post", "Only the author of this post or an admin can change it.");
            return;
        }

        String title = titleField.getText();
        String body = bodyArea.getText();
        PostStatus status = switch (statusBox.getValue()) {
            case "Published" -> PostStatus.PUBLISHED;
            case "Archived" -> PostStatus.ARCHIVED;
            default -> PostStatus.DRAFT;
        };
        List<String> tags = new ArrayList<>(selectedTags);
        long authorId = loaded != null ? loaded.authorId() : Session.currentUserId();

        // Disable Save while saving, so it cannot be clicked twice.
        // We do not use runDisabling, because validation errors go under the fields, not in a dialog.
        saveButton.setDisable(true);
        Async.run(() -> postId == null
                        ? postService.create(authorId, title, body, status, tags)
                        : postService.update(postId, title, body, status, tags),
                saved -> {
                    saveButton.setDisable(false);
                    shell.back();
                },
                error -> {
                    saveButton.setDisable(false);
                    Throwable cause = unwrap(error);
                    if (cause instanceof ValidationException validation) {
                        applyFieldErrors(validation);
                    } else {
                        Dialogs.error("Could not save the post", error);
                    }
                });
    }

    /** A failed task can wrap the real exception, so this takes it out. */
    private static Throwable unwrap(Throwable error) {
        return (error != null && error.getCause() != null
                && error.getClass() == RuntimeException.class)
                ? error.getCause()
                : error;
    }

    /** Shows each validation error under the field it belongs to. */
    private void applyFieldErrors(ValidationException validation) {
        validation.getErrors().forEach((field, message) -> {
            switch (field) {
                case "title" -> showError(titleField, titleError, message);
                case "body" -> showError(bodyArea, bodyError, message);
                default -> Dialogs.error("Could not save the post", message);
            }
        });
    }

    private void showError(Node field, Label error, String message) {
        if (!field.getStyleClass().contains("field-error")) {
            field.getStyleClass().add("field-error");
        }
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    private void clearError(Node field, Label error) {
        field.getStyleClass().remove("field-error");
        error.setVisible(false);
        error.setManaged(false);
    }
}
