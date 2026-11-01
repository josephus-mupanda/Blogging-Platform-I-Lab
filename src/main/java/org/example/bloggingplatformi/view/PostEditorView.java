package org.example.bloggingplatformi.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;
import org.example.bloggingplatformi.util.Excerpts;

/**
 * Create and edit a post.
 *
 * <p>Serves both cases from one screen: {@code postId == null} is a new post,
 * anything else loads and updates. The alternative — two near-identical forms —
 * drifts apart the moment a field is added to one of them.
 *
 * <p>Validation failures from the service are mapped back onto the field that
 * produced them, so the message appears under the input rather than in a dialog
 * the user has to dismiss before they can fix anything.
 */
public class PostEditorView implements View {

    private final AppShell shell;
    private final Long postId;

    private final PostService postService = new PostService();
    private final TagService tagService = new TagService();

    private final VBox root = new VBox(16);

    private final TextField titleField = new TextField();
    private final TextArea bodyArea = new TextArea();
    private final ComboBox<String> statusBox = new ComboBox<>();
    private final ComboBox<String> tagInput = new ComboBox<>();
    private final FlowPane tagChips = new FlowPane(7, 7);

    private final Label titleError = errorLabel();
    private final Label bodyError = errorLabel();

    private final Label slugLabel = Styles.label("Generated on save", "text-mono");
    private final Label excerptPreview = Styles.label("", "text-small");
    private final Label metaLabel = Styles.label("", "text-small");

    private final Set<String> selectedTags = new LinkedHashSet<>();
    private final Button saveButton = new Button("Save post");

    private Post loaded;

    public PostEditorView(AppShell shell, Long postId) {
        this.shell = shell;
        this.postId = postId;

        buildForm();
    }

    @Override
    public Node getRoot() {
        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        return scroller;
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
    // Form
    // -----------------------------------------------------------------

    private void buildForm() {
        titleField.setPromptText("A clear, specific headline");
        titleField.textProperty().addListener((observable, old, text) -> clearError(titleField, titleError));

        bodyArea.setPromptText("Write the post…");
        bodyArea.setWrapText(true);
        bodyArea.setPrefRowCount(18);
        bodyArea.textProperty().addListener((observable, old, text) -> {
            clearError(bodyArea, bodyError);
            // Live preview of the stored excerpt, so the denormalized column
            // is not a surprise after saving.
            excerptPreview.setText(Excerpts.from(text, 160));
        });

        statusBox.getItems().addAll("Draft", "Published", "Archived");
        statusBox.setValue("Draft");
        statusBox.setMaxWidth(Double.MAX_VALUE);

        VBox main = Styles.card(
                fieldGroup("Title", titleField, titleError),
                fieldGroup("Content", bodyArea, bodyError));
        HBox.setHgrow(main, Priority.ALWAYS);

        VBox side = new VBox(16, buildPublishCard(), buildTagCard(), buildMetaCard());
        // Narrow enough to leave the editor usable at the minimum window
        // width, capped so it does not sprawl on a wide display.
        side.setMinWidth(240);
        side.setPrefWidth(300);
        side.setMaxWidth(320);

        HBox columns = new HBox(16, main, side);
        root.getChildren().add(columns);
        root.setPadding(new Insets(24, 26, 28, 26));
    }

    private VBox buildPublishCard() {
        Label hint = Styles.label(
                "A published post gets a publication date; drafts and archived posts do not.",
                "text-caption");
        hint.setWrapText(true);

        return titledCard("Visibility",
                fieldGroup("Status", statusBox, null),
                hint);
    }

    private VBox buildTagCard() {
        tagInput.setEditable(true);
        tagInput.setPromptText("Add a tag and press Enter");
        tagInput.setMaxWidth(Double.MAX_VALUE);

        // Enter on the editor adds whatever was typed, whether or not it
        // matches an existing tag: free-text tagging is the point, and the
        // service creates anything new.
        tagInput.getEditor().setOnAction(event -> addTag(tagInput.getEditor().getText()));
        tagInput.setOnAction(event -> {
            String value = tagInput.getValue();
            if (value != null && !value.isBlank()) {
                addTag(value);
            }
        });

        Label hint = Styles.label("Names are lower-cased, so “Java” and “java” "
                + "are the same tag.", "text-caption");
        hint.setWrapText(true);

        tagChips.setPrefWrapLength(230);

        return titledCard("Tags", tagInput, tagChips, hint);
    }

    private VBox buildMetaCard() {
        VBox slugBox = new VBox(3, Styles.label("URL slug", "field-label"), slugLabel);
        slugLabel.setWrapText(true);

        VBox excerptBox = new VBox(3, Styles.label("Excerpt preview", "field-label"), excerptPreview);
        excerptPreview.setWrapText(true);

        metaLabel.setWrapText(true);

        return titledCard("Details", slugBox, excerptBox, metaLabel);
    }

    private VBox titledCard(String heading, Node... children) {
        VBox card = new VBox(11);
        card.getStyleClass().add("card");
        card.getChildren().add(Styles.label(heading.toUpperCase(Locale.ENGLISH), "text-eyebrow"));
        card.getChildren().addAll(children);
        return card;
    }

    private VBox fieldGroup(String label, Node field, Label error) {
        VBox group = new VBox(5, Styles.label(label, "field-label"), field);
        if (error != null) {
            group.getChildren().add(error);
        }
        if (field instanceof TextArea area) {
            VBox.setVgrow(area, Priority.ALWAYS);
        }
        return group;
    }

    private static Label errorLabel() {
        Label label = Styles.label("", "field-error-text");
        label.setVisible(false);
        label.setManaged(false);
        label.setWrapText(true);
        return label;
    }

    // -----------------------------------------------------------------
    // Tags
    // -----------------------------------------------------------------

    private void addTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String normalized = raw.strip().toLowerCase(Locale.ENGLISH);
        if (selectedTags.add(normalized)) {
            renderChips();
        }
        tagInput.getEditor().clear();
        tagInput.setValue(null);
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
                error -> { /* suggestions are a convenience; free text still works */ });
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
            Dialogs.error("No user selected",
                    "Choose a user in the top right before creating content.");
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

        // Disabled for the duration, so a slow save cannot be submitted twice.
        // The failure handler is explicit rather than using runDisabling,
        // because a validation error belongs under the field that caused it
        // rather than in a dialog the user must dismiss before fixing it.
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

    /** A task failure arrives wrapped; the useful exception is one level down. */
    private static Throwable unwrap(Throwable error) {
        return (error != null && error.getCause() != null
                && error.getClass() == RuntimeException.class)
                ? error.getCause()
                : error;
    }

    /** Puts each service-reported problem under the input that caused it. */
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
