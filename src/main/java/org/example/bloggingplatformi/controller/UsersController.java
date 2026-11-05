package org.example.bloggingplatformi.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Dates;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;
import org.example.bloggingplatformi.ui.Session;

/** Controller of users.fxml (admin only): the list of all accounts. */
public class UsersController {

    private final UserService userService = new UserService();
    private final Parent root;

    @FXML private TableView<User> table;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> createdColumn;
    @FXML private TableColumn<User, String> statusColumn;
    @FXML private Button statusButton;
    @FXML private Label statusMessage;

    public UsersController() {
        this.root = Fxml.load("users.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        // What each column shows for a user.
        usernameColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().username()));
        nameColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().displayName()));
        emailColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().email()));
        roleColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().role().name()));
        createdColumn.setCellValueFactory(row -> new SimpleStringProperty(Dates.format(row.getValue().createdAt())));
        statusColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().active() ? "Active" : "Inactive"));
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> showSelection(selected));
        showSelection(null);
        try {
            table.getItems().setAll(userService.findAll());
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the users: " + e.getMessage());
        }
    }

    private void showSelection(User user) {
        statusButton.setDisable(user == null || user.role().isAdmin());
        statusButton.setText(user != null && !user.active() ? "Reactivate account" : "Deactivate account");
        statusButton.getStyleClass().removeAll("primary", "danger");
        statusButton.getStyleClass().add(user != null && !user.active() ? "primary" : "danger");
        statusMessage.setText(user == null ? "Select an account to change its status."
                : user.role().isAdmin() ? "Administrator accounts are protected." : "");
    }

    @FXML
    private void changeStatus() {
        User selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || selected.role().isAdmin()) {
            return;
        }
        if (selected.active() && !Dialogs.confirm("Deactivate account?",
                selected.displayName() + " will be unable to sign in or make changes until reactivated.",
                "Deactivate account", true)) {
            return;
        }
        try {
            userService.setActive(Session.user(), selected.id(), !selected.active());
            var accounts = userService.findAll();
            table.getItems().setAll(accounts);
            accounts.stream().filter(user -> user.id() == selected.id()).findFirst()
                    .ifPresent(user -> table.getSelectionModel().select(user));
            statusMessage.setText(selected.active() ? "Account deactivated." : "Account reactivated.");
        } catch (ValidationException e) {
            statusMessage.setText(e.getMessage());
        } catch (RuntimeException e) {
            Dialogs.error("Could not change account status: " + e.getMessage());
        }
    }
}
