package org.example.bloggingplatformi.view;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostSort;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.PostSummary;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * The post list: search, filter, sort and page through everything in the
 * database.
 *
 * <p>This screen is where User Story 2.2 is actually delivered, so the filter
 * state lives in one immutable {@link PostFilter} that is rebuilt on each
 * change and handed straight to the DAO. Keeping it in a single value, rather
 * than reading six controls at query time, is what makes "which page am I on"
 * answerable and keeps the paging controls honest.
 */
public class PostListView implements View {

    /** All filter controls carry a sentinel for "no filter" rather than a null. */
    private static final User ANY_AUTHOR =
            new User(-1, "any", "any@any", "", "All authors", null,
                     org.example.bloggingplatformi.model.Role.READER, true, null, null);
    /**
     * Tag names are normalised to lower case by {@link Tag}, so the sentinel
     * is given a display name through the combo's converter instead of trying
     * to hold "All tags" as its name.
     */
    private static final Tag ANY_TAG = new Tag(-1, "all tags", "all-tags", null);
    private static final String ANY_TAG_LABEL = "All tags";

    private final AppShell shell;
    private final PostService postService = new PostService();
    private final UserService userService = new UserService();
    private final TagService tagService = new TagService();

    private final VBox root = new VBox(16);
    private final TableView<PostSummary> table = new TableView<>();
    private final ObservableList<PostSummary> rows = FXCollections.observableArrayList();

    private final TextField searchField = new TextField();
    private final ComboBox<User> authorFilter = new ComboBox<>();
    private final ComboBox<Tag> tagFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<PostSort> sortFilter = new ComboBox<>();

    private final Label rangeLabel = Styles.label("", "text-small");
    private final Button previousButton = new Button("Previous");
    private final Button nextButton = new Button("Next");

    /**
     * Debounce for the search box.
     *
     * <p>Without it every keystroke fires a query, so typing "indexing" issues
     * eight of them and the last four race each other to repaint the table.
     */
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(280));

    private PostFilter filter = PostFilter.all();
    private boolean filtersReady;

    public PostListView(AppShell shell) {
        this.shell = shell;

        buildTable();
        root.getChildren().addAll(buildFilterBar(), buildTableCard());
        root.setPadding(new Insets(24, 26, 28, 26));
        VBox.setVgrow(root.getChildren().get(1), Priority.ALWAYS);
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public String title() {
        return "Posts";
    }

    @Override
    public List<Node> actions() {
        Button refresh = new Button();
        refresh.setGraphic(Icons.of(Icons.REFRESH, 16));
        refresh.getStyleClass().addAll("btn-secondary", "btn-small");
        refresh.setTooltip(AppShell.tooltip("Reload from the database"));
        refresh.setOnAction(event -> load());

        return List.of(refresh,
                shell.buildPrimaryButton("New post", Icons.PLUS,
                        () -> shell.push(new PostEditorView(shell, null))));
    }

    @Override
    public void onShow() {
        if (!filtersReady) {
            loadFilterOptions();
        }
        load();
    }

    // -----------------------------------------------------------------
    // Filter bar
    // -----------------------------------------------------------------

    private Node buildFilterBar() {
        searchField.setPromptText("Search titles and content…");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(300);

        // The icon sits inside the field's left padding, which is what the
        // extra left padding in the .search-field rule reserves.
        StackPane searchBox = new StackPane(searchField,
                Icons.of(Icons.SEARCH, 15));
        StackPane.setAlignment(searchBox.getChildren().get(1), Pos.CENTER_LEFT);
        StackPane.setMargin(searchBox.getChildren().get(1), new Insets(0, 0, 0, 10));

        searchDebounce.setOnFinished(event -> {
            filter = filter.withKeyword(searchField.getText());
            load();
        });
        searchField.textProperty().addListener((observable, old, text) -> searchDebounce.playFromStart());

        authorFilter.setPrefWidth(165);
        authorFilter.setConverter(new SimpleConverter<>(User::displayName));
        authorFilter.valueProperty().addListener((observable, old, user) -> {
            if (filtersReady && user != null) {
                filter = filter.withAuthorId(user.userId() < 0 ? null : user.userId());
                load();
            }
        });

        tagFilter.setPrefWidth(150);
        tagFilter.setConverter(new SimpleConverter<>(
                tag -> tag.tagId() < 0 ? ANY_TAG_LABEL : tag.name()));
        tagFilter.valueProperty().addListener((observable, old, tag) -> {
            if (filtersReady && tag != null) {
                filter = filter.withTagId(tag.tagId() < 0 ? null : tag.tagId());
                load();
            }
        });

        statusFilter.setPrefWidth(140);
        statusFilter.getItems().addAll("All statuses", "Published", "Draft", "Archived");
        statusFilter.setValue("All statuses");
        statusFilter.valueProperty().addListener((observable, old, status) -> {
            if (filtersReady) {
                filter = filter.withStatus(switch (status) {
                    case "Published" -> PostStatus.PUBLISHED;
                    case "Draft" -> PostStatus.DRAFT;
                    case "Archived" -> PostStatus.ARCHIVED;
                    default -> null;
                });
                load();
            }
        });

        sortFilter.setPrefWidth(175);
        sortFilter.getItems().addAll(PostSort.values());
        sortFilter.setValue(PostSort.NEWEST);
        sortFilter.setConverter(new SimpleConverter<>(PostSort::label));
        sortFilter.valueProperty().addListener((observable, old, sort) -> {
            if (filtersReady && sort != null) {
                filter = filter.withSort(sort);
                load();
            }
        });

        Button clear = new Button("Clear");
        clear.getStyleClass().addAll("btn-ghost", "btn-small");
        clear.setOnAction(event -> resetFilters());

        // A FlowPane, not an HBox. These controls total roughly 1000px of
        // fixed width, which overflows the content area on a 1280px screen.
        // A FlowPane wraps them onto a second line instead of clipping the
        // last control or forcing a horizontal scrollbar.
        FlowPane bar = new FlowPane(10, 10,
                searchBox, authorFilter, tagFilter, statusFilter, sortFilter, clear);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("card");
        bar.setPadding(new Insets(12, 14, 12, 14));

        // The search box may shrink before the filters do.
        searchField.setMinWidth(180);
        return bar;
    }

    private void resetFilters() {
        filtersReady = false;
        searchField.clear();
        authorFilter.setValue(ANY_AUTHOR);
        tagFilter.setValue(ANY_TAG);
        statusFilter.setValue("All statuses");
        sortFilter.setValue(PostSort.NEWEST);
        filtersReady = true;

        filter = PostFilter.all();
        load();
    }

    private void loadFilterOptions() {
        Async.run(() -> {
            List<User> authors = new ArrayList<>();
            authors.add(ANY_AUTHOR);
            authors.addAll(userService.findAuthors());

            List<Tag> tags = new ArrayList<>();
            tags.add(ANY_TAG);
            tags.addAll(tagService.findAll());

            return List.of(authors, tags);
        }, loaded -> {
            @SuppressWarnings("unchecked")
            List<User> authors = (List<User>) loaded.get(0);
            @SuppressWarnings("unchecked")
            List<Tag> tags = (List<Tag>) loaded.get(1);

            authorFilter.getItems().setAll(authors);
            authorFilter.setValue(ANY_AUTHOR);
            tagFilter.getItems().setAll(tags);
            tagFilter.setValue(ANY_TAG);

            // Only now may the listeners fire queries; setting the values
            // above would otherwise trigger three redundant loads.
            filtersReady = true;
        }, error -> {
            authorFilter.getItems().setAll(ANY_AUTHOR);
            authorFilter.setValue(ANY_AUTHOR);
            tagFilter.getItems().setAll(ANY_TAG);
            tagFilter.setValue(ANY_TAG);
            filtersReady = true;
        });
    }

    // -----------------------------------------------------------------
    // Table
    // -----------------------------------------------------------------

    private Node buildTableCard() {
        VBox card = new VBox(table, buildPager());
        card.getStyleClass().add("card-flush");
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    private void buildTable() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(buildPlaceholder());

        TableColumn<PostSummary, PostSummary> titleColumn = new TableColumn<>("TITLE");
        titleColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        titleColumn.setCellFactory(column -> new TitleCell());
        titleColumn.setPrefWidth(340);
        titleColumn.setSortable(false);

        TableColumn<PostSummary, PostSummary> authorColumn = new TableColumn<>("AUTHOR");
        authorColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        authorColumn.setCellFactory(column -> new AuthorCell());
        authorColumn.setPrefWidth(170);
        authorColumn.setSortable(false);

        TableColumn<PostSummary, PostStatus> statusColumn = new TableColumn<>("STATUS");
        statusColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().status()));
        statusColumn.setCellFactory(column -> new BadgeCell());
        statusColumn.setPrefWidth(110);
        statusColumn.setSortable(false);

        TableColumn<PostSummary, String> engagementColumn = new TableColumn<>("ENGAGEMENT");
        engagementColumn.setCellValueFactory(data -> {
            PostSummary post = data.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                    Styles.number(post.viewCount()) + " views  ·  "
                  + Styles.number(post.commentCount()) + " comments");
        });
        engagementColumn.setPrefWidth(180);
        engagementColumn.setSortable(false);

        TableColumn<PostSummary, String> ratingColumn = new TableColumn<>("RATING");
        ratingColumn.setCellValueFactory(data -> {
            PostSummary post = data.getValue();
            String text = post.hasRating()
                    ? post.ratingDisplay() + "  (" + post.reviewCount() + ")"
                    : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        ratingColumn.setPrefWidth(100);
        ratingColumn.setSortable(false);

        TableColumn<PostSummary, PostSummary> actionsColumn = new TableColumn<>("");
        actionsColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        actionsColumn.setCellFactory(column -> new ActionsCell());
        actionsColumn.setPrefWidth(130);
        actionsColumn.setSortable(false);

        table.getColumns().setAll(List.of(titleColumn, authorColumn, statusColumn,
                engagementColumn, ratingColumn, actionsColumn));

        // Opening a post is the overwhelmingly common intent, so the whole row
        // responds to a double click rather than only the title.
        table.setRowFactory(view -> {
            var row = new javafx.scene.control.TableRow<PostSummary>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetail(row.getItem());
                }
            });
            return row;
        });
    }

    private Node buildPlaceholder() {
        Label heading = Styles.label("No posts match these filters", "text-subtitle");
        Label hint = Styles.label("Try clearing the search box or widening the filters.",
                "text-small");

        VBox empty = new VBox(6, heading, hint);
        empty.getStyleClass().add("empty-state");
        empty.setAlignment(Pos.CENTER);
        return empty;
    }

    private Node buildPager() {
        previousButton.getStyleClass().addAll("btn-secondary", "btn-small");
        nextButton.getStyleClass().addAll("btn-secondary", "btn-small");

        previousButton.setOnAction(event -> {
            filter = filter.previousPage();
            load();
        });
        nextButton.setOnAction(event -> {
            filter = filter.nextPage();
            load();
        });

        // Allowed to ellipsise rather than push the pager buttons off the edge.
        rangeLabel.setMinWidth(0);
        HBox pager = Styles.row(8, rangeLabel, Styles.spacer(), previousButton, nextButton);
        pager.getStyleClass().add("card-footer");
        return pager;
    }

    // -----------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------

    private void load() {
        PostFilter requested = filter;

        Async.run(() -> postService.search(requested),
                page -> {
                    // A slower earlier query must not overwrite a newer result.
                    if (requested != filter) {
                        return;
                    }
                    apply(page);
                },
                error -> {
                    rows.clear();
                    rangeLabel.setText("Could not load posts");
                    previousButton.setDisable(true);
                    nextButton.setDisable(true);
                    Dialogs.error("Could not load posts", error);
                });
    }

    private void apply(Page<PostSummary> page) {
        rows.setAll(page.content());

        if (page.totalElements() == 0) {
            rangeLabel.setText("No posts");
        } else {
            long first = page.firstElementIndex() + 1;
            long last = page.firstElementIndex() + page.content().size();
            rangeLabel.setText("Showing " + first + "–" + last
                    + " of " + Styles.number(page.totalElements())
                    + "   ·   page " + (page.pageNumber() + 1) + " of " + page.totalPages());
        }

        previousButton.setDisable(!page.hasPrevious());
        nextButton.setDisable(!page.hasNext());
    }

    private void openDetail(PostSummary post) {
        if (post != null) {
            shell.push(new PostDetailView(shell, post.postId()));
        }
    }

    private void confirmDelete(PostSummary post) {
        boolean confirmed = Dialogs.confirm("Delete post",
                "Delete “" + post.title() + "”?\n\n"
              + "Its comments, reviews and tag assignments go with it. "
              + "This cannot be undone.",
                "Delete post");

        if (!confirmed) {
            return;
        }
        Async.run(() -> {
            // Comments and reviews may live in MongoDB, where the PostgreSQL
            // cascade cannot reach them, so they are removed explicitly first.
            new org.example.bloggingplatformi.service.CommentService().deleteForPost(post.postId());
            new org.example.bloggingplatformi.service.ReviewService().deleteForPost(post.postId());
            return postService.delete(post.postId());
        }, deleted -> load());
    }

    // -----------------------------------------------------------------
    // Cells
    // -----------------------------------------------------------------

    private final class TitleCell extends TableCell<PostSummary, PostSummary> {
        @Override
        protected void updateItem(PostSummary post, boolean empty) {
            super.updateItem(post, empty);
            if (empty || post == null) {
                setGraphic(null);
                return;
            }
            Label title = Styles.label(post.title(), "text-body");
            title.setStyle("-fx-font-weight: 600;");

            String excerpt = post.excerpt() == null ? "" : post.excerpt();
            Label sub = Styles.label(truncate(excerpt, 72), "text-caption");

            VBox box = new VBox(1, title, sub);
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
        }
    }

    private final class AuthorCell extends TableCell<PostSummary, PostSummary> {
        @Override
        protected void updateItem(PostSummary post, boolean empty) {
            super.updateItem(post, empty);
            if (empty || post == null) {
                setGraphic(null);
                return;
            }
            Label name = Styles.label(post.authorDisplayName(), "text-body");
            Label when = Styles.label(Styles.relative(
                    post.publishedAt() != null ? post.publishedAt() : post.createdAt()),
                    "text-caption");

            HBox box = Styles.row(8, Styles.avatar(post.authorDisplayName(), true),
                    new VBox(0, name, when));
            setGraphic(box);
        }
    }

    private static final class BadgeCell extends TableCell<PostSummary, PostStatus> {
        @Override
        protected void updateItem(PostStatus status, boolean empty) {
            super.updateItem(status, empty);
            setGraphic(empty || status == null ? null : Styles.statusBadge(status));
        }
    }

    private final class ActionsCell extends TableCell<PostSummary, PostSummary> {
        @Override
        protected void updateItem(PostSummary post, boolean empty) {
            super.updateItem(post, empty);
            if (empty || post == null) {
                setGraphic(null);
                return;
            }
            Button open = iconAction(Icons.EYE, "Open", "btn-ghost");
            open.setOnAction(event -> openDetail(post));

            Button edit = iconAction(Icons.EDIT, "Edit", "btn-ghost");
            edit.setOnAction(event -> shell.push(new PostEditorView(shell, post.postId())));

            Button delete = iconAction(Icons.TRASH, "Delete", "btn-ghost");
            delete.setOnAction(event -> confirmDelete(post));

            setGraphic(Styles.row(2, open, edit, delete));
        }

        private Button iconAction(String icon, String tip, String style) {
            Button button = new Button();
            button.setGraphic(Icons.of(icon, 16));
            button.getStyleClass().addAll(style, "btn-small");
            button.setTooltip(AppShell.tooltip(tip));
            return button;
        }
    }

    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= limit ? flat : flat.substring(0, limit - 1) + "…";
    }

    /** Renders a combo item through one function; avoids six anonymous converters. */
    private static final class SimpleConverter<T> extends javafx.util.StringConverter<T> {
        private final java.util.function.Function<T, String> toText;

        SimpleConverter(java.util.function.Function<T, String> toText) {
            this.toText = toText;
        }

        @Override
        public String toString(T value) {
            return value == null ? "" : toText.apply(value);
        }

        @Override
        public T fromString(String string) {
            // The combos are not editable, so nothing is ever parsed back.
            return null;
        }
    }
}
