package org.example.bloggingplatformi.controller;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Fxml;

/** Controller of login.fxml: sign in, or create a blogger or reader account. */
public class LoginController {

    private final UserService userService = new UserService();
    private final Consumer<User> onSignedIn; // what to do after a successful sign in
    private final Parent root;

    // These fields are filled by FXMLLoader: same names as the fx:id in login.fxml.
    @FXML private VBox signInCard;
    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label message;
    @FXML private Label signInError;
    @FXML private VBox loginLoading;

    @FXML private VBox signUpCard;
    @FXML private TextField signupName;
    @FXML private TextField signupUsername;
    @FXML private TextField signupEmail;
    @FXML private PasswordField signupPassword;
    @FXML private RadioButton bloggerChoice;
    @FXML private Label signUpError;

    public LoginController(Consumer<User> onSignedIn) {
        this.onSignedIn = onSignedIn;
        this.root = Fxml.load("login.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    /**
     * The "Sign in" button. The password check runs in a second thread, so the window
     * does not freeze and the spinner can turn. Platform.runLater then goes back to the
     * JavaFX thread, the only one allowed to change the screen.
     */
    @FXML
    private void signIn() {
        String username = loginUsername.getText();
        String password = loginPassword.getText();
        signInError.setText("");
        signInCard.setDisable(true);
        loginLoading.setVisible(true);

        new Thread(() -> {
            try {
                User user = userService.login(username, password);
                Platform.runLater(() -> onSignedIn.accept(user));
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    signInError.setText(e.getMessage());
                    signInCard.setDisable(false);
                    loginLoading.setVisible(false);
                });
            }
        }).start();
    }

    /** The "Create account" button. After sign up, the user goes back to sign in. */
    @FXML
    private void signUp() {
        Role role = bloggerChoice.isSelected() ? Role.AUTHOR : Role.READER;
        try {
            userService.register(signupUsername.getText(), signupEmail.getText(),
                    signupPassword.getText(), signupName.getText(), role);
            String username = signupUsername.getText().strip();
            showSignIn();
            loginUsername.setText(username);
            message.setText("Account created. Sign in with your new password.");
        } catch (ValidationException e) {
            signUpError.setText(e.getMessage());
        } catch (RuntimeException e) {
            signUpError.setText("The database could not be reached: " + e.getMessage());
        }
    }

    @FXML
    private void showSignUp() {
        signUpError.setText("");
        signInCard.setVisible(false);
        signUpCard.setVisible(true);
    }

    @FXML
    private void showSignIn() {
        signInError.setText("");
        message.setText("");
        loginPassword.clear();
        signUpCard.setVisible(false);
        signInCard.setVisible(true);
    }
}
