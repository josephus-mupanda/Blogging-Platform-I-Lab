package org.example.bloggingplatformi.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Dates;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;

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
        try {
            table.getItems().setAll(userService.findAll());
        } catch (RuntimeException e) {
            Dialogs.error("Could not load the users: " + e.getMessage());
        }
    }
}
