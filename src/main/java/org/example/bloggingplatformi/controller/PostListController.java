package org.example.bloggingplatformi.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

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
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * Controller for post-list.fxml: search, filter, sort and page through all posts.
 * The current filters are kept in one {@link PostFilter} object, which is
 * replaced on every change and passed to the service.
 */
public class PostListController implements View {

    /** A fake "All authors" item with id -1. It means "no author filter". */
    private static final User ANY_AUTHOR =
            new User(-1, "any", "any@any", "", "All authors", null,
                     org.example.bloggingplatformi.model.Role.READER, true, null, null);
    /**
     * A fake "All tags" item with id -1.
     * Tag names are stored in lower case, so the label "All tags" comes from the converter.
     */
    private static final Tag ANY_TAG = new Tag(-1, "all tags", "all-tags", null);
    private static final String ANY_TAG_LABEL = "All tags";

    private final AppShell shell;
    private final PostService postService = new PostService();
    private final UserService userService = new UserService();
    private final TagService tagService = new TagService();

    private final ObservableList<PostSummary> rows = FXCollections.observableArrayList();
    private final Node root;

    // Filled by FXMLLoader from the fx:id values in post-list.fxml.
    @FXML private FlowPane filterBar;
    @FXML private StackPane searchBox;
    @FXML private TextField searchField;
    @FXML private ComboBox<User> authorFilter;
    @FXML private ComboBox<Tag> tagFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<PostSort> sortFilter;

    @FXML private TableView<PostSummary> table;
    @FXML private TableColumn<PostSummary, PostSummary> titleColumn;
    @FXML private TableColumn<PostSummary, PostSummary> authorColumn;
    @FXML private TableColumn<PostSummary, PostStatus> statusColumn;
    @FXML private TableColumn<PostSummary, String> engagementColumn;
    @FXML private TableColumn<PostSummary, String> ratingColumn;
    @FXML private TableColumn<PostSummary, PostSummary> actionsColumn;

    @FXML private Label rangeLabel;
    @FXML private Button previousButton;
    @FXML private Button nextButton;

    /** Waits until the user stops typing before searching, so we do not run a query on every key. */
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(280));

    private PostFilter filter = startFilter();
    private boolean filtersReady;

    public PostListController(AppShell shell) {
        this.shell = shell;
        this.root = Fxml.load("post-list.fxml", this);
    }

    /** Called by FXMLLoader once the @FXML fields are filled. */
    @FXML
    private void initialize() {
        setUpFilters();
        setUpTable();
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

        // Readers cannot write posts, so they only get the refresh button.
        if (!Session.canPublish()) {
            return List.of(refresh);
        }
        return List.of(refresh,
                shell.buildPrimaryButton("New post", Icons.PLUS,
                        () -> shell.push(new PostEditorController(shell, null))));
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

    /** What happens when a filter changes: build a new PostFilter and reload the table. */
    private void setUpFilters() {
        // The icon sits inside the field, in the extra left padding set by the .search-field CSS rule.
        Node searchIcon = Icons.of(Icons.SEARCH, 15);
        searchBox.getChildren().add(searchIcon);
        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 10));

        searchDebounce.setOnFinished(event -> {
            filter = filter.withKeyword(searchField.getText());
            load();
        });
        searchField.textProperty().addListener((observable, old, text) -> searchDebounce.playFromStart());

        authorFilter.setConverter(new SimpleConverter<>(User::displayName));
        authorFilter.valueProperty().addListener((observable, old, user) -> {
            if (filtersReady && user != null) {
                filter = filter.withAuthorId(user.userId() < 0 ? null : user.userId());
                load();
            }
        });

        tagFilter.setConverter(new SimpleConverter<>(
                tag -> tag.tagId() < 0 ? ANY_TAG_LABEL : tag.name()));
        tagFilter.valueProperty().addListener((observable, old, tag) -> {
            if (filtersReady && tag != null) {
                filter = filter.withTagId(tag.tagId() < 0 ? null : tag.tagId());
                load();
            }
        });

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

        sortFilter.getItems().addAll(PostSort.values());
        sortFilter.setValue(PostSort.NEWEST);
        sortFilter.setConverter(new SimpleConverter<>(PostSort::label));
        sortFilter.valueProperty().addListener((observable, old, sort) -> {
            if (filtersReady && sort != null) {
                filter = filter.withSort(sort);
                load();
            }
        });

        // Readers only see published posts, so the status filter is not shown to them.
        if (!Session.canPublish()) {
            filterBar.getChildren().remove(statusFilter);
        }
    }

    @FXML
    private void resetFilters() {
        filtersReady = false;
        searchField.clear();
        authorFilter.setValue(ANY_AUTHOR);
        tagFilter.setValue(ANY_TAG);
        statusFilter.setValue("All statuses");
        sortFilter.setValue(PostSort.NEWEST);
        filtersReady = true;

        filter = startFilter();
        load();
    }

    /**
     * Readers only see published posts. Bloggers and admins also see drafts and archived posts.
     */
    private static PostFilter startFilter() {
        return Session.canPublish() ? PostFilter.all() : PostFilter.all().withStatus(PostStatus.PUBLISHED);
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

            // Turn the listeners on only now, so setting the values above does not run extra queries.
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

    /** Says what each table column shows. The columns themselves are in post-list.fxml. */
    private void setUpTable() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        titleColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue()));
        titleColumn.setCellFactory(column -> new TitleCell());

        authorColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue()));
        authorColumn.setCellFactory(column -> new AuthorCell());

        statusColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().status()));
        statusColumn.setCellFactory(column -> new BadgeCell());

        engagementColumn.setCellValueFactory(data -> {
            PostSummary post = data.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                    Styles.number(post.viewCount()) + " views  ·  "
                  + (post.hasEngagement() ? Styles.number(post.commentCount()) : "—")
                  + " comments");
        });

        ratingColumn.setCellValueFactory(data -> {
            PostSummary post = data.getValue();
            String text = post.hasRating()
                    ? post.ratingDisplay() + "  (" + post.reviewCount() + ")"
                    : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });

        actionsColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue()));
        actionsColumn.setCellFactory(column -> new ActionsCell());

        // Double clicking anywhere on a row opens the post.
        table.setRowFactory(view -> {
            TableRow<PostSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetail(row.getItem());
                }
            });
            return row;
        });
    }

    @FXML
    private void previousPage() {
        filter = filter.previousPage();
        load();
    }

    @FXML
    private void nextPage() {
        filter = filter.nextPage();
        load();
    }

    // -----------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------

    private void load() {
        PostFilter requested = filter;

        Async.run(() -> postService.search(requested),
                page -> {
                    // Ignore the result if the filter changed while this query was running.
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
            shell.push(new PostDetailController(shell, post.postId()));
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
        Async.run(() -> postService.delete(post.postId()), deleted -> load());
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

            // Edit and Delete are only shown to the post's author and to the admin.
            if (!Session.canEditPost(post.authorId())) {
                setGraphic(Styles.row(2, open));
                return;
            }

            Button edit = iconAction(Icons.EDIT, "Edit", "btn-ghost");
            edit.setOnAction(event -> shell.push(new PostEditorController(shell, post.postId())));

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

    /** Shows a combo box item as text using the given function. */
    private static final class SimpleConverter<T> extends StringConverter<T> {
        private final Function<T, String> toText;

        SimpleConverter(Function<T, String> toText) {
            this.toText = toText;
        }

        @Override
        public String toString(T value) {
            return value == null ? "" : toText.apply(value);
        }

        @Override
        public T fromString(String string) {
            // Not needed, because the combo boxes are not editable.
            return null;
        }
    }
}
