package org.example.bloggingplatformi.view;

import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * The landing screen: counts across both stores, the most-used tags, and the
 * most recently published posts.
 *
 * <p>Each panel loads independently. A dashboard that waits for its slowest
 * query before showing anything feels broken even when every query is fast,
 * and if MongoDB is down the PostgreSQL tiles should still fill in.
 */
public class DashboardView implements View {

    private final AppShell shell;
    private final PostService postService;
    private final UserService userService;
    private final TagService tagService;
    private final CommentService commentService;
    private final ReviewService reviewService;

    private final VBox root = new VBox(20);

    private final MetricTile postsTile = new MetricTile("Posts", "#4f46e5", Icons.ARTICLE);
    private final MetricTile publishedTile = new MetricTile("Published", "#047857", Icons.EYE);
    private final MetricTile commentsTile = new MetricTile("Comments", "#b45309", Icons.COMMENT);
    private final MetricTile reviewsTile = new MetricTile("Reviews", "#be185d", Icons.STAR);
    private final MetricTile usersTile = new MetricTile("Accounts", "#0369a1", Icons.PERSON);
    private final MetricTile tagsTile = new MetricTile("Tags", "#6d28d9", Icons.TAG);

    private final VBox tagPanel = new VBox(10);
    private final VBox recentPanel = new VBox(10);

    public DashboardView(AppShell shell) {
        this.shell = shell;
        this.postService = new PostService();
        this.userService = new UserService();
        this.tagService = new TagService();
        this.commentService = new CommentService();
        this.reviewService = new ReviewService();

        root.getChildren().addAll(buildMetrics(), buildPanels());
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
        return "Dashboard";
    }

    @Override
    public String subtitle() {
        return "Activity across the relational and document stores";
    }

    @Override
    public List<Node> actions() {
        return List.of(shell.buildPrimaryButton("New post", Icons.PLUS,
                () -> shell.push(new PostEditorView(shell, null))));
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

    /** Width below which a metric tile stops being comfortably readable. */
    private static final double MIN_TILE_WIDTH = 250;

    private final GridPane metricGrid = new GridPane();
    private int metricColumns;

    private GridPane buildMetrics() {
        metricGrid.setHgap(14);
        metricGrid.setVgap(14);

        // The column count follows the available width instead of being fixed
        // at three. On a 1280px screen the content area is about 980px, which
        // holds three tiles; the same grid in a narrower window would squeeze
        // them past the point where the numbers stay legible.
        metricGrid.widthProperty().addListener((observable, old, width) ->
                layoutMetrics(width.doubleValue()));

        layoutMetrics(0);
        return metricGrid;
    }

    private void layoutMetrics(double available) {
        MetricTile[] tiles = {postsTile, publishedTile, commentsTile,
                              reviewsTile, usersTile, tagsTile};

        int columns = available <= 0
                ? 3
                : Math.max(1, Math.min(3, (int) (available / MIN_TILE_WIDTH)));

        // Rebuilding on every pixel of a resize would be wasteful and would
        // make the drag visibly stutter.
        if (columns == metricColumns) {
            return;
        }
        metricColumns = columns;

        metricGrid.getChildren().clear();
        metricGrid.getColumnConstraints().clear();

        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / columns);
            constraints.setHgrow(Priority.ALWAYS);
            metricGrid.getColumnConstraints().add(constraints);
        }
        for (int index = 0; index < tiles.length; index++) {
            metricGrid.add(tiles[index].node(), index % columns, index / columns);
        }
    }

    /** Below this the two panels no longer fit side by side and stack. */
    private static final double PANELS_SIDE_BY_SIDE_WIDTH = 760;

    private final GridPane panelGrid = new GridPane();
    private boolean panelsSideBySide = true;

    private GridPane buildPanels() {
        panelGrid.setHgap(14);
        panelGrid.setVgap(14);

        panelGrid.widthProperty().addListener((observable, old, width) ->
                layoutPanels(width.doubleValue()));

        layoutPanels(Double.MAX_VALUE);
        return panelGrid;
    }

    private void layoutPanels(double available) {
        boolean sideBySide = available >= PANELS_SIDE_BY_SIDE_WIDTH;
        if (sideBySide == panelsSideBySide && !panelGrid.getChildren().isEmpty()) {
            return;
        }
        panelsSideBySide = sideBySide;

        panelGrid.getChildren().clear();
        panelGrid.getColumnConstraints().clear();

        Node recent = panel("Recently published", recentPanel);
        Node tags = panel("Most used tags", tagPanel);

        if (sideBySide) {
            ColumnConstraints wide = new ColumnConstraints();
            wide.setPercentWidth(62);
            wide.setHgrow(Priority.ALWAYS);

            ColumnConstraints narrow = new ColumnConstraints();
            narrow.setPercentWidth(38);
            narrow.setHgrow(Priority.ALWAYS);

            panelGrid.getColumnConstraints().addAll(wide, narrow);
            panelGrid.add(recent, 0, 0);
            panelGrid.add(tags, 1, 0);
        } else {
            ColumnConstraints full = new ColumnConstraints();
            full.setPercentWidth(100);
            full.setHgrow(Priority.ALWAYS);

            panelGrid.getColumnConstraints().add(full);
            panelGrid.add(recent, 0, 0);
            panelGrid.add(tags, 0, 1);
        }
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

    // -----------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------

    private void loadMetrics() {
        // Grouped by store, so a Mongo outage leaves the SQL tiles populated
        // rather than blanking the whole row.
        Async.run(
                () -> new long[]{
                        postService.count(),
                        postService.countPublished(),
                        userService.count(),
                        tagService.count()},
                counts -> {
                    postsTile.setValue(counts[0]);
                    publishedTile.setValue(counts[1]);
                    usersTile.setValue(counts[2]);
                    tagsTile.setValue(counts[3]);
                },
                error -> {
                    postsTile.setUnavailable();
                    publishedTile.setUnavailable();
                    usersTile.setUnavailable();
                    tagsTile.setUnavailable();
                });

        Async.run(() -> new long[]{commentService.count(), reviewService.count()},
                counts -> {
                    commentsTile.setValue(counts[0]);
                    reviewsTile.setValue(counts[1]);
                },
                error -> {
                    commentsTile.setUnavailable();
                    reviewsTile.setUnavailable();
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

    /**
     * A tag row with a proportional bar.
     *
     * <p>The bar is scaled against the most-used tag rather than the total, so
     * the shape of the distribution stays visible even when one tag dominates.
     */
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
        row.setOnMouseClicked(event -> shell.push(new PostDetailView(shell, post.postId())));
        row.setStyle("-fx-cursor: hand;");
        return row;
    }

    private Node loadingRow() {
        return Styles.message("Loading…");
    }

    private Node emptyRow(String message) {
        return Styles.message(message);
    }

    // -----------------------------------------------------------------
    // Metric tile
    // -----------------------------------------------------------------

    /** A single KPI: accent strip, icon, label and value. */
    private static final class MetricTile {

        private final Label valueLabel = Styles.label("—", "text-metric");
        private final HBox node;

        MetricTile(String caption, String accentHex, String icon) {
            Region accent = new Region();
            accent.getStyleClass().add("metric-accent");
            accent.setStyle("-fx-background-color: " + accentHex + ";");
            accent.setMinHeight(42);

            Label captionLabel = Styles.label(caption.toUpperCase(), "text-eyebrow");

            VBox text = new VBox(2, captionLabel, valueLabel);
            HBox.setHgrow(text, Priority.ALWAYS);

            node = new HBox(14, accent, text,
                    Icons.of(icon, 22, Color.web(accentHex, 0.35)));
            node.setAlignment(Pos.CENTER_LEFT);
            node.getStyleClass().add("metric-tile");
        }

        Node node() {
            return node;
        }

        void setValue(long value) {
            valueLabel.setText(Styles.number(value));
        }

        /** An em dash, not a zero: an unreachable store is not an empty one. */
        void setUnavailable() {
            valueLabel.setText("—");
        }
    }
}
