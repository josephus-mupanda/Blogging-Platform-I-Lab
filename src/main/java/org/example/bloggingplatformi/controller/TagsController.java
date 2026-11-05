package org.example.bloggingplatformi.controller;

import java.util.Map;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;

/** Controller of tags.fxml (admin only). */
public class TagsController {

    private final TagService tagService = new TagService();
    private final Parent root;

    @FXML private ListView<Tag> tagList;
    @FXML private ListView<String> usageList;
    @FXML private TextField nameField;
    @FXML private Label errorLabel;

    public TagsController() {
        this.root = Fxml.load("tags.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        refresh();
    }

    private void refresh() {
        try {
            tagList.getItems().setAll(tagService.findAll());
            usageList.getItems().clear();
            for (Map.Entry<String, Integer> tag : tagService.usage().entrySet()) {
                usageList.getItems().add(tag.getKey() + ":  " + tag.getValue() + " posts");
            }
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
        } catch (ValidationException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    @FXML
    private void deleteTag() {
        Tag tag = tagList.getSelectionModel().getSelectedItem();
        if (tag == null) {
            errorLabel.setText("Select a tag first.");
            return;
        }
        if (Dialogs.confirm("Delete tag?", "\"" + tag.name() + "\" will be removed from its posts.", "Delete tag", true)) {
            tagService.delete(tag.id());
            errorLabel.setText("");
            refresh();
        }
    }
}
