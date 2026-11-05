package org.example.bloggingplatformi.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.ArchivedItem;
import org.example.bloggingplatformi.service.ArchiveService;
import org.example.bloggingplatformi.ui.Dates;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;

/** Administrator-only preview and restore screen. */
public class TrashController {

    private final ArchiveService archiveService = new ArchiveService();
    private final Parent root;

    @FXML private ChoiceBox<ArchivedItem.Kind> kindFilter;
    @FXML private ListView<ArchivedItem> archivedItems;
    @FXML private TextArea archivedPreview;
    @FXML private Button restoreButton;
    @FXML private Label trashStatus;

    public TrashController() {
        root = Fxml.load("trash.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        archivedItems.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ArchivedItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label title = new Label(item.title());
                title.getStyleClass().add("field-label");
                title.setWrapText(true);
                Label time = new Label("Moved to Trash " + Dates.format(item.archivedAt()));
                time.getStyleClass().add("small");
                VBox row = new VBox(4, title, time);
                row.prefWidthProperty().bind(widthProperty().subtract(32));
                row.maxWidthProperty().bind(row.prefWidthProperty());
                setGraphic(row);
            }
        });
        archivedItems.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            archivedPreview.setText(selected == null ? "" : selected.content());
            archivedPreview.positionCaret(0);
            restoreButton.setDisable(selected == null);
            trashStatus.setText("");
        });
        kindFilter.getItems().setAll(ArchivedItem.Kind.values());
        kindFilter.setValue(ArchivedItem.Kind.POST);
        kindFilter.valueProperty().addListener((observable, previous, current) -> refresh());
        refresh();
    }

    @FXML
    private void refresh() {
        archivedItems.getSelectionModel().clearSelection();
        archivedItems.getItems().clear();
        trashStatus.setText("");
        try {
            archivedItems.getItems().setAll(archiveService.list(kindFilter.getValue(), Session.user()));
        } catch (ValidationException e) {
            trashStatus.setText(e.getMessage());
        } catch (RuntimeException e) {
            trashStatus.setText("Could not load Trash. Check the database connection and try Refresh.");
        }
    }

    @FXML
    private void restore() {
        ArchivedItem item = archivedItems.getSelectionModel().getSelectedItem();
        if (item == null) {
            return;
        }
        try {
            archiveService.restore(item, Session.user());
            refresh();
            trashStatus.setText("Restored.");
        } catch (ValidationException e) {
            trashStatus.setText(e.getMessage());
        } catch (RuntimeException e) {
            Dialogs.error("Could not restore the item: " + e.getMessage());
        }
    }
}
