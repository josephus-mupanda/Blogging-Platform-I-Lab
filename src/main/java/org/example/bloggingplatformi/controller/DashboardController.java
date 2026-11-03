package org.example.bloggingplatformi.controller;

import java.util.List;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostSort;
import org.example.bloggingplatformi.model.PostSummary;
import org.example.bloggingplatformi.service.CommentService;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.ReviewService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * Controller for dashboard.fxml: counts from both databases, the most used tags,
 * and the latest published posts.
 * Each panel loads on its own, so one slow or failed query does not block the others.
 */
public class DashboardController implements View {

    /** Shown instead of 0 when a database cannot be reached, because it is not empty. */
    private static final String UNAVAILABLE = "—";

    /** The smallest width at which a metric tile is still easy to read. */
    private static final double MIN_TILE_WIDTH = 250;

    /** Below this width the two panels are stacked instead of side by side. */
    private static final double PANELS_SIDE_BY_SIDE_WIDTH = 760;

    private final AppShell shell;
    private final PostService postService = new PostService();
    private final UserService userService = new UserService();
    private final TagService tagService = new TagService();
    private final CommentService commentService = new CommentService();
    private final ReviewService reviewService = new ReviewService();
    private final Node root;

    // Filled by FXMLLoader from the fx:id values in dashboard.fxml.
    @FXML private GridPane metricGrid;
    @FXML private HBox postsTile, publishedTile, commentsTile, reviewsTile, usersTile, tagsTile;
    @FXML private Label postsValue, publishedValue, commentsValue, reviewsValue, usersValue, tagsValue;
    @FXML private StackPane postsIcon, publishedIcon, commentsIcon, reviewsIcon, usersIcon, tagsIcon;
    @FXML private GridPane panelGrid;
    @FXML private VBox recentCard;
    @FXML private VBox tagsCard;
    @FXML private VBox recentPanel;
    @FXML private VBox tagPanel;

    private int metricColumns;
    private boolean panelsSideBySide = true;

    public DashboardController(AppShell shell) {
        this.shell = shell;
        this.root = Fxml.load("dashboard.fxml", this);
    }

    @FXML
    private void initialize() {
        // Icons are SVG paths kept in Icons.java; the colours match the strips in the FXML.
        postsIcon.getChildren().add(Icons.of(Icons.ARTICLE, 22, Color.web("#4f46e5", 0.35)));
        publishedIcon.getChildren().add(Icons.of(Icons.EYE, 22, Color.web("#047857", 0.35)));
        commentsIcon.getChildren().add(Icons.of(Icons.COMMENT, 22, Color.web("#b45309", 0.35)));
        reviewsIcon.getChildren().add(Icons.of(Icons.STAR, 22, Color.web("#be185d", 0.35)));
        usersIcon.getChildren().add(Icons.of(Icons.PERSON, 22, Color.web("#0369a1", 0.35)));
        tagsIcon.getChildren().add(Icons.of(Icons.TAG, 22, Color.web("#6d28d9", 0.35)));

        // The number of columns depends on the width, so nothing is squeezed in a small window.
        metricGrid.widthProperty().addListener((observable, old, width) ->
                layoutMetrics(width.doubleValue()));
        panelGrid.widthProperty().addListener((observable, old, width) ->
                layoutPanels(width.doubleValue()));
        layoutMetrics(0);
        layoutPanels(Double.MAX_VALUE);
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public String subtitle() {
        return "Activity across the relational and document stores";
    }

    @Override
    public List<Node> actions() {
        // Readers cannot write posts, so they do not get the button.
        if (!Session.canPublish()) {
            return List.of();
        }
        return List.of(shell.buildPrimaryButton("New post", Icons.PLUS,
                () -> shell.push(new PostEditorController(shell, null))));
    }

    @Override
    public void onShow() {
        loadMetrics();
        loadTags();
        loadRecent();
    }

    // -----------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------

    /** Puts the six tiles in 1 to 3 columns. */
    private void layoutMetrics(double available) {
        HBox[] tiles = {postsTile, publishedTile, commentsTile, reviewsTile, usersTile, tagsTile};

        int columns = available <= 0
                ? 3
                : Math.max(1, Math.min(3, (int) (available / MIN_TILE_WIDTH)));

        // Only rebuild when the column count changes, not on every resize.
        if (columns == metricColumns) {
            return;
        }
        metricColumns = columns;

        metricGrid.getChildren().clear();
        metricGrid.getColumnConstraints().clear();
        for (int column = 0; column < columns; column++) {
            metricGrid.getColumnConstraints().add(column(100.0 / columns));
        }
        for (int index = 0; index < tiles.length; index++) {
            metricGrid.add(tiles[index], index % columns, index / columns);
        }
    }

    /** Puts the two panels side by side on a wide window, one under the other on a narrow one. */
    private void layoutPanels(double available) {
        boolean sideBySide = available >= PANELS_SIDE_BY_SIDE_WIDTH;
        if (sideBySide == panelsSideBySide && !panelGrid.getColumnConstraints().isEmpty()) {
            return;
        }
        panelsSideBySide = sideBySide;

        panelGrid.getChildren().clear();
        panelGrid.getColumnConstraints().clear();
        if (sideBySide) {
            panelGrid.getColumnConstraints().addAll(column(62), column(38));
            panelGrid.add(recentCard, 0, 0);
            panelGrid.add(tagsCard, 1, 0);
        } else {
            panelGrid.getColumnConstraints().add(column(100));
            panelGrid.add(recentCard, 0, 0);
            panelGrid.add(tagsCard, 0, 1);
        }
    }

    private static ColumnConstraints column(double percentWidth) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(percentWidth);
        constraints.setHgrow(Priority.ALWAYS);
        return constraints;
    }

    // -----------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------

    private void loadMetrics() {
        // Two separate calls, one per database, so if MongoDB is down the PostgreSQL tiles still load.
        Async.run(
                () -> new long[]{
                        postService.count(),
                        postService.countPublished(),
                        userService.count(),
                        tagService.count()},
                counts -> {
                    postsValue.setText(Styles.number(counts[0]));
                    publishedValue.setText(Styles.number(counts[1]));
                    usersValue.setText(Styles.number(counts[2]));
                    tagsValue.setText(Styles.number(counts[3]));
                },
                error -> {
                    postsValue.setText(UNAVAILABLE);
                    publishedValue.setText(UNAVAILABLE);
                    usersValue.setText(UNAVAILABLE);
                    tagsValue.setText(UNAVAILABLE);
                });

        Async.run(() -> new long[]{commentService.count(), reviewService.count()},
                counts -> {
                    commentsValue.setText(Styles.number(counts[0]));
                    reviewsValue.setText(Styles.number(counts[1]));
                },
                error -> {
                    commentsValue.setText(UNAVAILABLE);
                    reviewsValue.setText(UNAVAILABLE);
                });
    }

    private void loadTags() {
        tagPanel.getChildren().setAll(loadingRow());

        Async.run(() -> tagService.usageCounts(8),
                counts -> {
                    if (counts.isEmpty()) {
                        tagPanel.getChildren().setAll(emptyRow("No tags yet"));
                        return;
                    }
                    long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
                    tagPanel.getChildren().clear();
                    for (Map.Entry<String, Long> entry : counts.entrySet()) {
                        tagPanel.getChildren().add(tagRow(entry.getKey(), entry.getValue(), max));
                    }
                },
                error -> tagPanel.getChildren().setAll(emptyRow("Tags unavailable")));
    }

    /** A tag row with a bar. The bar is relative to the most used tag, not the total. */
    private Node tagRow(String name, long count, long max) {
        Label nameLabel = Styles.label(name, "text-body");
        nameLabel.setMinWidth(110);
        nameLabel.setMaxWidth(110);

        Node track = Styles.proportionBar(max == 0 ? 0 : (double) count / max, 150);

        Label countLabel = Styles.label(Styles.number(count), "text-small");
        countLabel.setMinWidth(34);
        countLabel.setAlignment(Pos.CENTER_RIGHT);

        return Styles.row(10, nameLabel, track, countLabel);
    }

    private void loadRecent() {
        recentPanel.getChildren().setAll(loadingRow());

        PostFilter filter = PostFilter.published().withSort(PostSort.RECENTLY_PUBLISHED)
                .withPageSize(6);

        Async.run(() -> postService.search(filter),
                page -> {
                    if (page.isEmpty()) {
                        recentPanel.getChildren().setAll(emptyRow("Nothing published yet"));
                        return;
                    }
                    recentPanel.getChildren().clear();
                    for (PostSummary post : page.content()) {
                        recentPanel.getChildren().add(recentRow(post));
                    }
                },
                error -> recentPanel.getChildren().setAll(emptyRow("Posts unavailable")));
    }

    private Node recentRow(PostSummary post) {
        Label title = Styles.label(post.title(), "text-body");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);

        Label meta = Styles.label(post.authorDisplayName()
                + "  ·  " + Styles.relative(post.publishedAt())
                + "  ·  " + Styles.number(post.viewCount()) + " views", "text-caption");

        VBox text = new VBox(2, title, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = Styles.row(11, Styles.avatar(post.authorDisplayName(), true), text,
                Styles.badge((post.hasEngagement() ? Styles.number(post.commentCount()) : "—") + " · "
                        + post.ratingDisplay(), "neutral"));
        row.setPadding(new Insets(6, 0, 6, 0));
        row.setOnMouseClicked(event -> shell.push(new PostDetailController(shell, post.postId())));
        row.setStyle("-fx-cursor: hand;");
        return row;
    }

    private Node loadingRow() {
        return Styles.message("Loading…");
    }

    private Node emptyRow(String message) {
        return Styles.message(message);
    }
}
