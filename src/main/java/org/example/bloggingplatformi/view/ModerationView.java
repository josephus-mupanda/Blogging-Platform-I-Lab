package org.example.bloggingplatformi.view;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Comment;
import org.example.bloggingplatformi.service.CommentService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * The moderation queue: every comment that is flagged or hidden.
 *
 * <p>Backed by a partial index in both stores — {@code idx_comments_moderation}
 * in PostgreSQL and the matching partial index in MongoDB — because the rows
 * this screen wants are a small minority of a large table, which is exactly
 * when a partial index earns its keep.
 */
public class ModerationView implements View {

    private final AppShell shell;
    private final CommentService commentService = new CommentService();

    private final VBox root = new VBox(16);
    private final VBox queue = new VBox(10);
    private final Label countLabel = Styles.label("", "text-small");

    public ModerationView(AppShell shell) {
        this.shell = shell;

        VBox header = new VBox(3, Styles.subtitle("Flagged and hidden comments"), countLabel);
        header.getStyleClass().add("card-header");

        VBox body = new VBox(queue);
        body.setPadding(new Insets(14, 18, 16, 18));

        VBox card = new VBox(header, body);
        card.getStyleClass().add("card-flush");

        root.getChildren().add(card);
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
                    "Switch to an account with the ADMIN role to use the moderation queue."));
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
        open.setOnAction(event -> shell.push(new PostDetailView(shell, comment.postId())));

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

    private void act(java.util.function.Supplier<?> operation) {
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
