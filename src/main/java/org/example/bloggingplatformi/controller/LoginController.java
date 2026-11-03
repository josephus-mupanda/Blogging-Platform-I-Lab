package org.example.bloggingplatformi.controller;

import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Dialogs;
import org.example.bloggingplatformi.ui.Fxml;

/**
 * Controller for login.fxml: sign in, or create a new blogger or reader account.
 * After creating an account the user comes back to the sign in card and signs in.
 * When the user is signed in, {@code onSignedIn} is called and the app opens the main window.
 */
public class LoginController {

    private final UserService userService = new UserService();
    private final Consumer<User> onSignedIn;
    private final Parent root;

    // Filled by FXMLLoader from the fx:id values in login.fxml.
    @FXML private VBox signInCard;
    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label successLabel;
    @FXML private Label signInError;
    @FXML private Button signInButton;

    @FXML private VBox signUpCard;
    @FXML private TextField signupName;
    @FXML private TextField signupUsername;
    @FXML private TextField signupEmail;
    @FXML private PasswordField signupPassword;
    @FXML private RadioButton bloggerChoice;
    @FXML private RadioButton readerChoice;
    @FXML private Label signUpError;
    @FXML private Button signUpButton;

    public LoginController(Consumer<User> onSignedIn) {
        this.onSignedIn = onSignedIn;
        this.root = Fxml.load("login.fxml", this);
    }

    public Parent getRoot() {
        return root;
    }

    // -----------------------------------------------------------------
    // Sign in
    // -----------------------------------------------------------------

    /** Shows the sign in card. Also called by the app after "Sign out". */
    @FXML
    public void showSignIn() {
        hide(signInError);
        hide(successLabel);
        loginPassword.clear();
        showCard(signInCard, signUpCard);
        signInButton.setDefaultButton(true);
        signUpButton.setDefaultButton(false);
        loginUsername.requestFocus();
    }

    @FXML
    private void signIn() {
        hide(signInError);
        startLoading(signInButton, "Signing in…");
        String username = loginUsername.getText();
        String password = loginPassword.getText();

        Async.run(() -> userService.login(username, password),
                user -> {
                    stopLoading(signInButton, "Sign in");
                    onSignedIn.accept(user);
                },
                failure -> {
                    stopLoading(signInButton, "Sign in");
                    show(signInError, Dialogs.humanise(failure));
                });
    }

    // -----------------------------------------------------------------
    // Sign up
    // -----------------------------------------------------------------

    @FXML
    private void showSignUp() {
        hide(signUpError);
        hide(successLabel);
        signupPassword.clear();
        showCard(signUpCard, signInCard);
        signUpButton.setDefaultButton(true);
        signInButton.setDefaultButton(false);
        signupName.requestFocus();
    }

    @FXML
    private void signUp() {
        hide(signUpError);
        startLoading(signUpButton, "Creating account…");
        String name = signupName.getText();
        String username = signupUsername.getText();
        String email = signupEmail.getText();
        String password = signupPassword.getText();
        Role role = bloggerChoice.isSelected() ? Role.AUTHOR : Role.READER;

        // After the account is created, the user goes back to the sign in card
        // and signs in with the new username and password.
        Async.run(() -> userService.register(username, email, password, name, role),
                user -> {
                    stopLoading(signUpButton, "Create account");
                    clearSignUpForm();
                    showSignIn();
                    loginUsername.setText(user.username());
                    loginPassword.requestFocus();
                    show(successLabel, "Account created. Sign in with your new password.");
                },
                failure -> {
                    stopLoading(signUpButton, "Create account");
                    show(signUpError, Dialogs.humanise(failure));
                });
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void showCard(VBox shown, VBox hidden) {
        shown.setVisible(true);
        shown.setManaged(true);
        hidden.setVisible(false);
        hidden.setManaged(false);
    }

    /** Shows a spinning circle in the button and disables it, so it cannot be clicked twice. */
    private void startLoading(Button button, String text) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        button.setGraphic(spinner);
        button.setText(text);
        button.setDisable(true);
    }

    private void stopLoading(Button button, String text) {
        button.setGraphic(null);
        button.setText(text);
        button.setDisable(false);
    }

    private void clearSignUpForm() {
        signupName.clear();
        signupUsername.clear();
        signupEmail.clear();
        signupPassword.clear();
        readerChoice.setSelected(true);
    }

    private static void show(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private static void hide(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }
}
