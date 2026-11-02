package org.example.bloggingplatformi.view;

import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * The tag vocabulary: everything defined, how heavily each is used, and a way
 * to add or retire one.
 *
 * <p>Deleting a tag removes its {@code post_tags} rows by cascade but leaves
 * every post intact, which the confirmation says explicitly — "delete tag" is
 * ambiguous enough to be worth spelling out.
 */
public class TagsView implements View {

    private final AppShell shell;
    private final TagService tagService = new TagService();

    private final VBox root = new VBox(16);
    private final FlowPane cloud = new FlowPane(9, 9);
    private final VBox usageList = new VBox(9);
    private final TextField newTagField = new TextField();

    public TagsView(AppShell shell) {
        this.shell = shell;

        root.getChildren().addAll(buildCreateCard(), buildPanels());
        root.setPadding(new Insets(24, 26, 28, 26));
    }

    @Override
    public Node getRoot() {
        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        return scroller;
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

    private Node buildCreateCard() {
        newTagField.setPromptText("New tag name");
        newTagField.setPrefWidth(260);
        newTagField.setOnAction(event -> createTag());

        Button add = new Button("Add tag");
        add.setGraphic(Icons.of(Icons.PLUS, 15));
        add.getStyleClass().add("btn-primary");
        add.setOnAction(event -> createTag());

        Label hint = Styles.label(
                "Names are stored lower-case, so “Java” and “java” cannot both exist.",
                "text-caption");

        // Wraps rather than pushing the hint text past the window edge.
        javafx.scene.layout.FlowPane row = new javafx.scene.layout.FlowPane(10, 8,
                newTagField, add, hint);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card");
        row.setPadding(new Insets(13, 16, 13, 16));
        return row;
    }

    private Node buildPanels() {
        VBox cloudPanel = panel("All tags", new VBox(cloud));
        VBox usagePanel = panel("Usage", usageList);

        HBox columns = new HBox(16, cloudPanel, usagePanel);
        HBox.setHgrow(cloudPanel, Priority.ALWAYS);
        cloudPanel.setMinWidth(0);
        usagePanel.setMinWidth(250);
        usagePanel.setPrefWidth(320);
        usagePanel.setMaxWidth(340);
        return columns;
    }

    private VBox panel(String heading, VBox body) {
        VBox header = new VBox(Styles.subtitle(heading));
        header.getStyleClass().add("card-header");

        VBox content = new VBox(body);
        content.setPadding(new Insets(14, 18, 16, 18));

        VBox panel = new VBox(header, content);
        panel.getStyleClass().add("card-flush");
        return panel;
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
     * A message placed inside the tag cloud.
     *
     * <p>{@code cloud} is a {@link FlowPane}, which hands each child its
     * preferred width rather than the pane's. A wrapping label therefore
     * reports one long line and is clipped at the panel edge, so the wrap
     * width is bound to the pane explicitly.
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
