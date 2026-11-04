package org.example.bloggingplatformi;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.bloggingplatformi.controller.LoginController;
import org.example.bloggingplatformi.controller.MainController;
import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.Mongo;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.ui.Session;

/**
 * Starts the JavaFX app. The window opens on the sign in screen.
 * After sign in it shows the main window; "Sign out" goes back to sign in.
 */
public class BloggingPlatformApplication extends Application {

    private Scene scene;

    @Override
    public void start(Stage stage) {
        scene = new Scene(new LoginController(this::showMainWindow).getRoot(), 1200, 760);
        scene.getStylesheets().add(getClass().getResource("/org/example/bloggingplatformi/css/app.css").toExternalForm());

        stage.setTitle("Blogfold - Smart Blogging Platform");
        stage.setScene(scene);
        stage.show();
    }

    /** Called by the sign in screen with the user who signed in. */
    private void showMainWindow(User user) {
        Session.signIn(user);
        scene.setRoot(new MainController(this::showLoginScreen).getRoot());
    }

    /** Called by the "Sign out" button. */
    private void showLoginScreen() {
        Session.signOut();
        scene.setRoot(new LoginController(this::showMainWindow).getRoot());
    }

    /** Called when the window closes. */
    @Override
    public void stop() {
        Database.close();
        Mongo.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
