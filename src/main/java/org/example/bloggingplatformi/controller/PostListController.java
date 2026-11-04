package org.example.bloggingplatformi.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.PostService;
import org.example.bloggingplatformi.service.TagService;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Dates;
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

    @FXML private TextField searchField;
    @FXML private ComboBox<User> authorBox;
    @FXML private ComboBox<Tag> tagBox;
    @FXML private TableView<Post> table;
    @FXML private TableColumn<Post, String> titleColumn;
    @FXML private TableColumn<Post, String> authorColumn;
    @FXML private TableColumn<Post, String> dateColumn;
    @FXML private Button openButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
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
        // What each column shows for a post.
        titleColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().title()));
        authorColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().authorName()));
        dateColumn.setCellValueFactory(row -> new SimpleStringProperty(Dates.format(row.getValue().createdAt())));

        // Readers cannot write posts, so they do not see "New post".
        newButton.setVisible(Session.canWritePosts());

        // Enable the buttons only when a row is selected, and Edit / Delete only for the author or the admin.
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldPost, post) -> updateButtons(post));
        updateButtons(null);

        try {
            authorBox.getItems().setAll(userService.findAuthors());
            tagBox.getItems().setAll(tagService.findAll());
            loadPage();
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the posts: " + e.getMessage());
        }
    }

    /** Reads the current page from the database with the current filters. */
    private void loadPage() {
        String keyword = searchField.getText();
        Long authorId = authorBox.getValue() == null ? null : authorBox.getValue().id();
        Long tagId = tagBox.getValue() == null ? null : tagBox.getValue().id();

        totalPages = postService.countPages(keyword, authorId, tagId);
        table.getItems().setAll(postService.search(keyword, authorId, tagId, page));

        pageLabel.setText("Page " + (page + 1) + " of " + totalPages);
        previousButton.setDisable(page == 0);
        nextButton.setDisable(page >= totalPages - 1);
    }

    private void updateButtons(Post post) {
        openButton.setDisable(post == null);
        boolean canEdit = post != null && Session.canEditPost(post.authorId());
        editButton.setDisable(!canEdit);
        deleteButton.setDisable(!canEdit);
        editButton.setVisible(Session.canWritePosts());
        deleteButton.setVisible(Session.canWritePosts());
    }

    @FXML
    private void search() {
        page = 0; // new filters: go back to the first page
        loadPage();
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        authorBox.setValue(null);
        tagBox.setValue(null);
        search();
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
    private void openPost() {
        main.showPost(table.getSelectionModel().getSelectedItem().id());
    }

    @FXML
    private void editPost() {
        main.showEditor(table.getSelectionModel().getSelectedItem().id());
    }

    @FXML
    private void newPost() {
        main.showEditor(null);
    }

    @FXML
    private void deletePost() {
        Post post = table.getSelectionModel().getSelectedItem();
        if (Dialogs.confirm("Delete \"" + post.title() + "\"? Its comments and reviews are deleted too.")) {
            try {
                postService.delete(post.id());
                loadPage();
            } catch (RuntimeException e) {
                Dialogs.error("Could not delete the post: " + e.getMessage());
            }
        }
    }
}
