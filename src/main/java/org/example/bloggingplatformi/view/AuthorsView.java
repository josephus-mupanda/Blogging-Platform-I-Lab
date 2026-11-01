package org.example.bloggingplatformi.view;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Icons;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.ui.View;

/**
 * The account list.
 *
 * <p>Deactivating is offered; deleting is not. The schema cascades a user's
 * posts away with them, so a mis-click here would silently destroy content,
 * and {@code is_active} exists precisely so that is not the only option.
 */
public class AuthorsView implements View {

    private static final int PAGE_SIZE = 100;

    private final AppShell shell;
    private final UserService userService = new UserService();

    private final VBox root = new VBox(16);
    private final TableView<User> table = new TableView<>();
    private final ObservableList<User> rows = FXCollections.observableArrayList();

    public AuthorsView(AppShell shell) {
        this.shell = shell;

        buildTable();

        VBox card = new VBox(table);
        card.getStyleClass().add("card-flush");
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().add(card);
        root.setPadding(new Insets(24, 26, 28, 26));
        VBox.setVgrow(card, Priority.ALWAYS);
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

    private void buildTable() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Styles.label("No accounts found.", "text-small"));

        TableColumn<User, User> personColumn = new TableColumn<>("ACCOUNT");
        personColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        personColumn.setCellFactory(column -> new PersonCell());
        personColumn.setPrefWidth(280);
        personColumn.setSortable(false);

        TableColumn<User, String> emailColumn = new TableColumn<>("EMAIL");
        emailColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().email()));
        emailColumn.setPrefWidth(240);
        emailColumn.setSortable(false);

        TableColumn<User, Role> roleColumn = new TableColumn<>("ROLE");
        roleColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().role()));
        roleColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Role role, boolean empty) {
                super.updateItem(role, empty);
                setGraphic(empty || role == null ? null : Styles.roleBadge(role));
            }
        });
        roleColumn.setPrefWidth(110);
        roleColumn.setSortable(false);

        TableColumn<User, User> statusColumn = new TableColumn<>("STATUS");
        statusColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
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
        statusColumn.setPrefWidth(120);
        statusColumn.setSortable(false);

        TableColumn<User, User> actionsColumn = new TableColumn<>("");
        actionsColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        actionsColumn.setCellFactory(column -> new ActionsCell());
        actionsColumn.setPrefWidth(190);
        actionsColumn.setSortable(false);

        table.getColumns().setAll(List.of(personColumn, emailColumn, roleColumn,
                statusColumn, actionsColumn));
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
