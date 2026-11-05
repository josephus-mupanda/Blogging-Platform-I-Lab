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

/** Controller of post-list.fxml: search, filter, page through and manage posts. */
public class PostListController {

    private final MainController main;
    private final PostService postService = new PostService();
    private final UserService userService = new UserService();
    private final TagService tagService = new TagService();
    private final Parent root;

    private int page = 0;      // the page shown now, starting at 0
    private int totalPages = 1;
    private boolean clearingFilters;

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

    public PostListController(MainController main) {
        this.main = main;
        this.root = Fxml.load("post-list.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        newButton.setVisible(Session.canWritePosts());
        newButton.managedProperty().bind(newButton.visibleProperty());

        try {
            authorBox.getItems().setAll(userService.findAuthors());
            tagBox.getItems().setAll(tagService.findAll());
            showFilterValue(authorBox);
            showFilterValue(tagBox);
            authorBox.valueProperty().addListener((observable, previous, current) -> search());
            tagBox.valueProperty().addListener((observable, previous, current) -> search());
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

    /** Reads the current page from the database with the current filters. */
    private List<Post> loadPage() {
        String keyword = searchField.getText();
        Long authorId = authorBox.getValue() == null ? null : authorBox.getValue().id();
        Long tagId = tagBox.getValue() == null ? null : tagBox.getValue().id();

        totalPages = postService.countPages(keyword, authorId, tagId);
        page = Math.min(page, totalPages - 1);
        List<Post> posts = postService.search(keyword, authorId, tagId, page);
        postCards.getChildren().setAll(posts.stream().map(main::postCard).toList());
        resultsScroll.setVvalue(0);
        emptyResults.setVisible(posts.isEmpty());

        pageLabel.setText("Page " + (page + 1) + " of " + totalPages);
        previousButton.setDisable(page == 0);
        nextButton.setDisable(page >= totalPages - 1);
        return posts;
    }

    @FXML
    private void searchOnEnter() {
        page = 0;
        List<Post> posts = loadPage();
        if (!searchField.getText().isBlank() && posts.size() == 1) {
            main.showPost(posts.getFirst().id());
        }
    }

    @FXML
    private void search() {
        if (!clearingFilters) {
            page = 0;
            loadPage();
        }
    }

    @FXML
    private void clearFilters() {
        clearingFilters = true;
        try {
            searchField.clear();
            authorBox.getSelectionModel().clearSelection();
            tagBox.getSelectionModel().clearSelection();
            authorBox.setValue(null);
            tagBox.setValue(null);
        } finally {
            clearingFilters = false;
        }
        search();
        searchField.requestFocus();
    }

    @FXML
    private void previousPage() {
        page--;
        loadPage();
    }

    @FXML
    private void nextPage() {
        page++;
        loadPage();
    }

    @FXML
    private void newPost() {
        main.showEditor(null);
    }
}
