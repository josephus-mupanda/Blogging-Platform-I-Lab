package org.example.bloggingplatformi.controller;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.TagChips;

/** Controller of post-list.fxml: search, filter, page through and manage posts. */
public class PostListController {

    private final MainController main;
    private final PostService postService = new PostService();
    private final UserService userService = new UserService();
    private final TagService tagService = new TagService();
    private final Parent root;

    private int page = 0;      // the page shown now, starting at 0
    private int totalPages = 1;
    private boolean updatingFilters;
    private String appliedKeyword = "";

    @FXML private TextField searchField;
    @FXML private ComboBox<User> authorBox;
    @FXML private ComboBox<Tag> tagBox;
    @FXML private VBox postCards;
    @FXML private ScrollPane resultsScroll;
    @FXML private Label emptyResults;
    @FXML private Button newButton;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Label pageLabel;
    @FXML private Label resultCount;
    @FXML private Label filterNotice;
    @FXML private FlowPane activeTag;

    public PostListController(MainController main) {
        this.main = main;
        this.root = Fxml.load("post-list.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        filterNotice.visibleProperty().bind(filterNotice.textProperty().isNotEmpty());
        newButton.setVisible(Session.canWritePosts());
        newButton.managedProperty().bind(newButton.visibleProperty());

        try {
            reloadFilters();
            showFilterValue(authorBox);
            showFilterValue(tagBox);
            authorBox.valueProperty().addListener((observable, previous, current) -> applyFilters());
            tagBox.valueProperty().addListener((observable, previous, current) -> applyFilters());
            loadPage();
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the posts: " + e.getMessage());
        }
    }

    private <T> void showFilterValue(ComboBox<T> filter) {
        filter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? filter.getPromptText() : item.toString());
            }
        });
    }

    void refresh() {
        try {
            reloadFilters();
            loadPage();
        } catch (RuntimeException e) {
            Dialogs.error("Could not refresh the posts: " + e.getMessage());
        }
    }

    private void reloadFilters() {
        updatingFilters = true;
        try {
            replaceOptions(authorBox, userService.findAuthors());
            replaceOptions(tagBox, tagService.findAll());
        } finally {
            updatingFilters = false;
        }
    }

    private <T> void replaceOptions(ComboBox<T> filter, List<T> options) {
        T selected = filter.getValue();
        filter.getItems().setAll(options);
        filter.setValue(selected != null && options.contains(selected) ? selected : null);
    }

    /** Reads the current page from the database with the applied filters. */
    private void loadPage() {
        Long authorId = authorBox.getValue() == null ? null : authorBox.getValue().id();
        Tag tag = tagBox.getValue();
        Long tagId = tag == null ? null : tag.id();

        int matches = postService.countMatches(appliedKeyword, authorId, tagId);
        totalPages = Math.max(1, (matches + PostService.PAGE_SIZE - 1) / PostService.PAGE_SIZE);
        page = Math.min(page, totalPages - 1);
        List<Post> posts = postService.search(appliedKeyword, authorId, tagId, page);
        postCards.getChildren().setAll(posts.stream().map(main::postCard).toList());
        emptyResults.setVisible(posts.isEmpty());
        resultCount.setText(matches + (matches == 1 ? " article" : " articles"));
        activeTag.getChildren().clear();
        if (tag != null) {
            activeTag.getChildren().add(TagChips.removable(new Label(tag.name()), "Remove tag filter " + tag.name(),
                    this::clearTagFilter));
        }
        activeTag.setVisible(tag != null);

        pageLabel.setText("Page " + (page + 1) + " of " + totalPages);
        previousButton.setDisable(page == 0);
        nextButton.setDisable(page >= totalPages - 1);
    }

    @FXML
    private void search() {
        appliedKeyword = searchField.getText().strip();
        applyFilters();
    }

    private void applyFilters() {
        if (!updatingFilters) {
            filterNotice.setText("");
            page = 0;
            showPage();
        }
    }

    @FXML
    private void clearFilters() {
        resetFilters(null);
        searchField.requestFocus();
    }

    private void clearTagFilter() {
        updatingFilters = true;
        try {
            tagBox.getSelectionModel().clearSelection();
            tagBox.setValue(null);
        } finally {
            updatingFilters = false;
        }
        applyFilters();
        tagBox.requestFocus();
    }

    void filterByTag(Tag tag) {
        try {
            reloadFilters();
            resetFilters(tagBox.getItems().contains(tag) ? tag : null);
            if (!tagBox.getItems().contains(tag)) {
                filterNotice.setText("This tag is no longer available. Showing all posts.");
            }
        } catch (RuntimeException e) {
            Dialogs.error("Could not load this tag: " + e.getMessage());
        }
    }

    private void resetFilters(Tag tag) {
        updatingFilters = true;
        try {
            searchField.clear();
            authorBox.getSelectionModel().clearSelection();
            tagBox.getSelectionModel().clearSelection();
            authorBox.setValue(null);
            tagBox.setValue(tag);
        } finally {
            updatingFilters = false;
        }
        search();
    }

    @FXML
    private void previousPage() {
        page--;
        showPage();
    }

    @FXML
    private void nextPage() {
        page++;
        showPage();
    }

    private void showPage() {
        try {
            loadPage();
            resultsScroll.setVvalue(0);
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the posts: " + e.getMessage());
        }
    }

    @FXML
    private void newPost() {
        main.showEditor(null);
    }
}
