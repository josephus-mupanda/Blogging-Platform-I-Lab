package org.example.bloggingplatformi.controller;

import java.util.List;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * Controller for authors.fxml (admin only): a table of all user accounts.
 * Users can be deactivated but not deleted, because deleting a user also deletes their posts.
 */
public class AuthorsController implements View {

    private static final int PAGE_SIZE = 100;

    private final AppShell shell;
    private final UserService userService = new UserService();
    private final ObservableList<User> rows = FXCollections.observableArrayList();
    private final Node root;

    // Filled by FXMLLoader from the fx:id values in authors.fxml.
    @FXML private TableView<User> table;
    @FXML private TableColumn<User, User> personColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, Role> roleColumn;
    @FXML private TableColumn<User, User> statusColumn;
    @FXML private TableColumn<User, User> actionsColumn;

    public AuthorsController(AppShell shell) {
        this.shell = shell;
        this.root = Fxml.load("authors.fxml", this);
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public String title() {
        return "Authors";
    }

    @Override
    public String subtitle() {
        return "Accounts on the platform";
    }

    @Override
    public List<Node> actions() {
        Button refresh = new Button();
        refresh.setGraphic(Icons.of(Icons.REFRESH, 16));
        refresh.getStyleClass().addAll("btn-secondary", "btn-small");
        refresh.setTooltip(AppShell.tooltip("Reload accounts"));
        refresh.setOnAction(event -> load());
        return List.of(refresh);
    }

    @Override
    public void onShow() {
        load();
    }

    /** Says what each table column shows. Called by FXMLLoader after the fields are filled. */
    @FXML
    private void initialize() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        personColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue()));
        personColumn.setCellFactory(column -> new PersonCell());

        emailColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().email()));

        roleColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().role()));
        roleColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Role role, boolean empty) {
                super.updateItem(role, empty);
                setGraphic(empty || role == null ? null : Styles.roleBadge(role));
            }
        });

        statusColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(user.active()
                        ? Styles.badge("Active", "success")
                        : Styles.badge("Deactivated", "neutral"));
            }
        });

        actionsColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue()));
        actionsColumn.setCellFactory(column -> new ActionsCell());
    }

    private void load() {
        Async.run(() -> userService.findAll(PAGE_SIZE, 0),
                rows::setAll,
                error -> {
                    rows.clear();
                    Dialogs.error("Could not load accounts", error);
                });
    }

    private void toggleActive(User user) {
        String verb = user.active() ? "Deactivate" : "Reactivate";
        boolean confirmed = Dialogs.confirm(verb + " account",
                verb + " " + user.displayName() + "?\n\n"
              + "Their posts and comments are untouched either way.",
                verb);
        if (confirmed) {
            Async.run(() -> userService.setActive(user.userId(), !user.active()),
                    updated -> load());
        }
    }

    private final class PersonCell extends TableCell<User, User> {
        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
                return;
            }
            Label name = Styles.label(user.displayName(), "text-body");
            name.setStyle("-fx-font-weight: 600;");
            Label handle = Styles.label("@" + user.username(), "text-caption");

            HBox box = Styles.row(10, Styles.avatar(user.displayName(), false),
                    new VBox(1, name, handle));
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
        }
    }

    private final class ActionsCell extends TableCell<User, User> {
        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
                return;
            }
            Button posts = new Button("Posts");
            posts.getStyleClass().addAll("btn-ghost", "btn-small");
            posts.setTooltip(AppShell.tooltip("Filter the post list by this author"));
            posts.setOnAction(event -> shell.navigate(AppShell.Destination.POSTS));

            Button toggle = new Button(user.active() ? "Deactivate" : "Reactivate");
            toggle.getStyleClass().addAll("btn-secondary", "btn-small");
            toggle.setOnAction(event -> toggleActive(user));

            setGraphic(Styles.row(5, posts, toggle));
        }
    }
}
