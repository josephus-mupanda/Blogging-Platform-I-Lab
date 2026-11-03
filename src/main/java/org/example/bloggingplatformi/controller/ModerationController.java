package org.example.bloggingplatformi.controller;

import java.util.List;
import java.util.function.Supplier;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.service.CommentService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * Controller for moderation.fxml (admin only): lists every flagged or hidden comment
 * so the admin can restore, hide or delete it.
 * The query uses a partial index in both PostgreSQL and MongoDB, since only a few comments match.
 */
public class ModerationController implements View {

    private final AppShell shell;
    private final CommentService commentService = new CommentService();
    private final Node root;

    // Filled by FXMLLoader from the fx:id values in moderation.fxml.
    @FXML private Label countLabel;
    @FXML private VBox queue;

    public ModerationController(AppShell shell) {
        this.shell = shell;
        this.root = Fxml.load("moderation.fxml", this);
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public String title() {
        return "Moderation";
    }

    @Override
    public String subtitle() {
        return "Comments needing a decision";
    }

    @Override
    public List<Node> actions() {
        Button refresh = new Button();
        refresh.setGraphic(Icons.of(Icons.REFRESH, 16));
        refresh.getStyleClass().addAll("btn-secondary", "btn-small");
        refresh.setTooltip(AppShell.tooltip("Reload the queue"));
        refresh.setOnAction(event -> load());
        return List.of(refresh);
    }

    @Override
    public void onShow() {
        load();
    }

    private void load() {
        if (!Session.canModerate()) {
            countLabel.setText("");
            queue.getChildren().setAll(message(
                    "Admins only",
                    "Sign in with an ADMIN account to use the moderation queue."));
            return;
        }

        queue.getChildren().setAll(Styles.label("Loading…", "text-small"));

        Async.run(() -> commentService.moderationQueue(50),
                flagged -> {
                    countLabel.setText(flagged.isEmpty()
                            ? "Nothing waiting"
                            : flagged.size() + " awaiting review  ·  stored in "
                                    + commentService.activeStore());

                    if (flagged.isEmpty()) {
                        queue.getChildren().setAll(message(
                                "The queue is clear",
                                "No comment is currently flagged or hidden."));
                        return;
                    }
                    queue.getChildren().clear();
                    flagged.forEach(comment -> queue.getChildren().add(row(comment)));
                },
                error -> {
                    countLabel.setText("unavailable");
                    queue.getChildren().setAll(message(
                            "Could not load the queue", Dialogs.humanise(error)));
                });
    }

    private Node row(Comment comment) {
        Label author = Styles.label(comment.displayName(), "text-body");
        author.setStyle("-fx-font-weight: 600;");

        Label when = Styles.label(Styles.relative(comment.createdAt()), "text-caption");

        HBox header = Styles.row(8, Styles.avatar(comment.displayName(), true), author, when,
                Styles.statusBadge(comment.status()), Styles.spacer(),
                Styles.label("post #" + comment.postId(), "text-caption"));

        Label body = Styles.label(comment.body(), "comment-body");
        body.setWrapText(true);

        Button open = new Button("Open post");
        open.getStyleClass().addAll("btn-ghost", "btn-small");
        open.setOnAction(event -> shell.push(new PostDetailController(shell, comment.postId())));

        Button restore = new Button("Restore");
        restore.getStyleClass().addAll("btn-secondary", "btn-small");
        restore.setOnAction(event -> act(() -> commentService.restore(comment.commentId())));

        Button hide = new Button("Hide");
        hide.getStyleClass().addAll("btn-secondary", "btn-small");
        hide.setOnAction(event -> act(() -> commentService.hide(comment.commentId())));

        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("btn-danger", "btn-small");
        delete.setOnAction(event -> confirmDelete(comment));

        HBox actions = Styles.row(6, open, Styles.spacer(), restore, hide, delete);

        VBox box = new VBox(8, header, body, actions);
        box.getStyleClass().add("comment");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void act(Supplier<?> operation) {
        Async.run(operation::get, result -> load());
    }

    private void confirmDelete(Comment comment) {
        boolean confirmed = Dialogs.confirm("Delete comment",
                "Delete this comment and every reply beneath it?\n\nThis cannot be undone.",
                "Delete");
        if (confirmed) {
            act(() -> commentService.delete(comment.commentId()));
        }
    }

    private Node message(String heading, String detail) {
        Label title = Styles.label(heading, "text-subtitle");
        Label body = Styles.label(detail, "text-small");
        body.setWrapText(true);
        body.setMaxWidth(440);

        VBox box = new VBox(6, title, body);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
