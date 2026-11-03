package org.example.bloggingplatformi.controller;

import java.util.List;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * Controller for tags.fxml (admin only): shows all tags and how often each is used,
 * and lets the admin add or delete a tag.
 * Deleting a tag removes it from posts but does not delete the posts.
 */
public class TagsController implements View {

    private final TagService tagService = new TagService();
    private final Node root;

    // Filled by FXMLLoader from the fx:id values in tags.fxml.
    @FXML private TextField newTagField;
    @FXML private Button addButton;
    @FXML private FlowPane cloud;
    @FXML private VBox usageList;

    public TagsController() {
        this.root = Fxml.load("tags.fxml", this);
    }

    @FXML
    private void initialize() {
        addButton.setGraphic(Icons.of(Icons.PLUS, 15));
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public String title() {
        return "Tags";
    }

    @Override
    public String subtitle() {
        return "The controlled vocabulary posts are filed under";
    }

    @Override
    public List<Node> actions() {
        Button refresh = new Button();
        refresh.setGraphic(Icons.of(Icons.REFRESH, 16));
        refresh.getStyleClass().addAll("btn-secondary", "btn-small");
        refresh.setTooltip(AppShell.tooltip("Reload tags"));
        refresh.setOnAction(event -> onShow());
        return List.of(refresh);
    }

    @Override
    public void onShow() {
        loadCloud();
        loadUsage();
    }

    private void loadCloud() {
        cloud.getChildren().setAll(cloudMessage("Loading…"));

        Async.run(tagService::findAll,
                tags -> {
                    if (tags.isEmpty()) {
                        cloud.getChildren().setAll(cloudMessage("No tags defined yet."));
                        return;
                    }
                    cloud.getChildren().clear();
                    tags.forEach(tag -> cloud.getChildren().add(tagChip(tag)));
                },
                error -> cloud.getChildren().setAll(
                        cloudMessage(Dialogs.humanise(error))));
    }

    /**
     * A message shown inside the tag cloud.
     * Its width is bound to the cloud, otherwise the FlowPane would not wrap the text.
     */
    private Node cloudMessage(String text) {
        Label label = Styles.message(text);
        label.maxWidthProperty().bind(cloud.widthProperty());
        return label;
    }

    private Node tagChip(Tag tag) {
        Label name = Styles.label(tag.name(), "text-small");

        Button remove = new Button("×");
        remove.getStyleClass().add("chip-remove");
        remove.setTooltip(AppShell.tooltip("Delete this tag"));
        remove.setOnAction(event -> confirmDelete(tag));

        HBox chip = new HBox(2, name, remove);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("chip-removable");
        return chip;
    }

    private void loadUsage() {
        usageList.getChildren().setAll(Styles.message("Loading…"));

        Async.run(() -> tagService.usageCounts(15),
                counts -> {
                    if (counts.isEmpty()) {
                        usageList.getChildren().setAll(Styles.message("Nothing tagged yet."));
                        return;
                    }
                    long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
                    usageList.getChildren().clear();
                    for (Map.Entry<String, Long> entry : counts.entrySet()) {
                        usageList.getChildren().add(usageRow(entry.getKey(), entry.getValue(), max));
                    }
                },
                error -> usageList.getChildren().setAll(Styles.message(Dialogs.humanise(error))));
    }

    private Node usageRow(String name, long count, long max) {
        Label nameLabel = Styles.label(name, "text-body");
        nameLabel.setMinWidth(110);
        nameLabel.setMaxWidth(110);

        Node track = Styles.proportionBar(max == 0 ? 0 : (double) count / max, 140);

        Label countLabel = Styles.label(Styles.number(count), "text-small");
        countLabel.setMinWidth(32);
        countLabel.setAlignment(Pos.CENTER_RIGHT);

        return Styles.row(10, nameLabel, track, countLabel);
    }

    @FXML
    private void createTag() {
        String name = newTagField.getText();
        if (name == null || name.isBlank()) {
            return;
        }
        Async.run(() -> tagService.findOrCreate(name),
                id -> {
                    newTagField.clear();
                    onShow();
                },
                error -> Dialogs.error("Could not create the tag", error));
    }

    private void confirmDelete(Tag tag) {
        boolean confirmed = Dialogs.confirm("Delete tag",
                "Delete the tag “" + tag.name() + "”?\n\n"
              + "It is removed from every post that carries it. The posts themselves "
              + "are not affected.",
                "Delete tag");
        if (confirmed) {
            Async.run(() -> tagService.delete(tag.tagId()), deleted -> onShow());
        }
    }
}
