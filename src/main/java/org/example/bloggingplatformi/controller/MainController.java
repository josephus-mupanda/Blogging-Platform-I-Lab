package org.example.bloggingplatformi.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.ui.Dates;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;

/**
 * Controller of main.fxml: the menu, the top bar, and switching between screens.
 * The other controllers call showPosts(), showPost(id)... to go to another screen.
 */
public class MainController {

    private final Runnable onSignOut;
    private final Parent root;

    @FXML private Button tagsButton;
    @FXML private Button usersButton;
    @FXML private Label screenTitle;
    @FXML private Label userLabel;
    @FXML private StackPane content;

    public MainController(Runnable onSignOut) {
        this.onSignOut = onSignOut;
        this.root = Fxml.load("main.fxml", this);
    }

    /** Called by FXMLLoader after the @FXML fields are filled. */
    @FXML
    private void initialize() {
        User user = Session.user();
        userLabel.setText(user.displayName() + " (" + user.role().name().toLowerCase() + ")");

        // Only the admin sees the Tags and Users screens.
        tagsButton.setVisible(Session.isAdmin());
        tagsButton.setManaged(Session.isAdmin()); // managed = false: it takes no space when hidden
        usersButton.setVisible(Session.isAdmin());
        usersButton.setManaged(Session.isAdmin());

        showDashboard();
    }

    public Parent getRoot() {
        return root;
    }

    /** Puts a screen in the middle of the window. */
    private void show(String title, Parent screen) {
        screenTitle.setText(title);
        content.getChildren().setAll(screen);
    }

    @FXML
    public void showDashboard() {
        show("Dashboard", new DashboardController(this).getRoot());
    }

    @FXML
    public void showPosts() {
        show("Posts", new PostListController(this).getRoot());
    }

    public void showPost(long postId) {
        show("Post", new PostDetailController(this, postId).getRoot());
    }

    Button postCard(Post post) {
        Label title = new Label(post.title());
        title.setWrapText(true);
        title.getStyleClass().add("subtitle");
        Label byline = new Label(post.authorName() + "  ·  " + Dates.format(post.createdAt()));
        byline.setWrapText(true);
        byline.getStyleClass().add("small");

        Button card = new Button();
        card.setGraphic(new VBox(4, title, byline));
        card.setAccessibleText(post.title() + ", by " + post.authorName());
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("post-card");
        card.setOnAction(event -> showPost(post.id()));
        card.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();
                card.fire();
            }
        });
        return card;
    }

    /** postId = null for a new post. */
    public void showEditor(Long postId) {
        show(postId == null ? "New post" : "Edit post", new PostEditorController(this, postId).getRoot());
    }

    @FXML
    public void showTags() {
        show("Tags", new TagsController().getRoot());
    }

    @FXML
    public void showUsers() {
        show("Users", new UsersController().getRoot());
    }

    @FXML
    private void signOut() {
        if (Dialogs.confirm("Do you want to sign out?")) {
            onSignOut.run();
        }
    }
}
