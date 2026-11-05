package org.example.bloggingplatformi.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.TagChips;

/** Controller of tags.fxml (admin only). */
public class TagsController {

    private final TagService tagService = new TagService();
    private final MainController main;
    private final Parent root;

    @FXML private FlowPane tagCatalog;
    @FXML private Label emptyCatalog;
    @FXML private TextField nameField;
    @FXML private Label errorLabel;

    public TagsController(MainController main) {
        this.main = main;
        this.root = Fxml.load("tags.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
        nameField.textProperty().addListener((observable, previous, current) -> errorLabel.setText(""));
        refresh();
    }

    private void refresh() {
        try {
            var tags = tagService.findAll();
            var usage = tagService.usage();
            tagCatalog.getChildren().setAll(tags.stream().map(tag -> {
                int count = usage.getOrDefault(tag.name(), 0);
                String posts = count + (count == 1 ? " post" : " posts");
                var link = TagChips.link(tag.name() + "  ·  " + count, "Browse " + posts + " tagged " + tag.name(),
                        () -> main.showPostsForTag(tag));
                return TagChips.removable(link, "Delete tag " + tag.name(), () -> deleteTag(tag));
            }).toList());
            emptyCatalog.setVisible(tags.isEmpty());
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the tags: " + e.getMessage());
        }
    }

    @FXML
    private void addTag() {
        try {
            tagService.create(nameField.getText());
            nameField.clear();
            errorLabel.setText("");
            refresh();
            nameField.requestFocus();
        } catch (ValidationException e) {
            errorLabel.setText(e.getMessage());
            nameField.requestFocus();
        } catch (RuntimeException e) {
            Dialogs.error("Could not add the tag: " + e.getMessage());
        }
    }

    private void deleteTag(Tag tag) {
        if (Dialogs.confirm("Delete tag?", "\"" + tag.name() + "\" will be removed from its posts.", "Delete tag", true)) {
            try {
                tagService.delete(tag.id());
                errorLabel.setText("");
                refresh();
                nameField.requestFocus();
            } catch (RuntimeException e) {
                Dialogs.error("Could not delete the tag: " + e.getMessage());
            }
        }
    }
}
