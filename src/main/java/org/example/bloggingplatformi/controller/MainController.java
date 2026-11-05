package org.example.bloggingplatformi.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.Tag;
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
    private PostListController postList;
    private PostEditorController editor;

    @FXML private Button tagsButton;
    @FXML private Button usersButton;
    @FXML private Button dashboardButton;
    @FXML private Button postsButton;
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
    private void show(String title, Parent screen, Button activeButton) {
        editor = null;
        screenTitle.setText(title);
        content.getChildren().setAll(screen);
        for (Button button : new Button[] {dashboardButton, postsButton, tagsButton, usersButton}) {
            button.getStyleClass().remove("active");
        }
        activeButton.getStyleClass().add("active");
    }

    @FXML
    public void showDashboard() {
        if (confirmDiscardDraft()) {
            show("Dashboard", new DashboardController(this).getRoot(), dashboardButton);
        }
    }

    @FXML
    public void showPosts() {
        showPostsForTag(null);
    }

    void showPostsForTag(Tag tag) {
        if (!confirmDiscardDraft()) {
            return;
        }
        if (postList == null) {
            postList = new PostListController(this);
        } else if (tag == null) {
            postList.refresh();
        }
        if (tag != null) {
            postList.filterByTag(tag);
        }
        show("Posts", postList.getRoot(), postsButton);
    }

    public void showPost(long postId) {
        if (confirmDiscardDraft()) {
            show("Post", new PostDetailController(this, postId).getRoot(), postsButton);
        }
    }

    Button postCard(Post post) {
        Label title = new Label(post.title());
        title.setMinWidth(0);
        title.getStyleClass().add("subtitle");
        Label byline = new Label(post.authorName() + "  ·  " + Dates.format(post.createdAt()));
        byline.setMinWidth(0);
        byline.getStyleClass().add("small");
        String firstLine = post.body().lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
        Label preview = new Label(firstLine);
        preview.setMinWidth(0);
        preview.setPrefWidth(240);
        preview.setMaxWidth(Double.MAX_VALUE);
        VBox copy = new VBox(4, title, byline, preview);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label arrow = new Label("›");
        arrow.getStyleClass().add("post-arrow");
        HBox graphic = new HBox(10, copy, arrow);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMaxHeight(Region.USE_PREF_SIZE);

        Button card = new Button();
        card.setGraphic(graphic);
        card.setMinWidth(0);
        card.setMaxHeight(Region.USE_PREF_SIZE);
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
        if (confirmDiscardDraft()) {
            try {
                PostEditorController next = new PostEditorController(this, postId);
                show(postId == null ? "New post" : "Edit post", next.getRoot(), postsButton);
                editor = next;
            } catch (RuntimeException e) {
                Dialogs.error("Could not open the post editor: " + e.getMessage());
            }
        }
    }

    @FXML
    public void showTags() {
        if (confirmDiscardDraft()) {
            show("Tags", new TagsController(this).getRoot(), tagsButton);
        }
    }

    @FXML
    public void showUsers() {
        if (Session.isAdmin() && confirmDiscardDraft()) {
            show("Users", new UsersController().getRoot(), usersButton);
        }
    }

    public boolean confirmDiscardDraft() {
        return editor == null || !editor.hasChanges()
                || Dialogs.confirm("Discard changes?", "Changes to this draft will be lost.", "Discard changes", true);
    }

    @FXML
    private void signOut() {
        boolean hasDraft = editor != null && editor.hasChanges();
        String message = hasDraft ? "Changes to this draft will be lost." : "You can sign in again at any time.";
        if (Dialogs.confirm("Sign out?", message, "Sign out", hasDraft)) {
            onSignOut.run();
        }
    }
}
