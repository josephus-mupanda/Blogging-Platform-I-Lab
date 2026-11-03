package org.example.bloggingplatformi;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import org.example.bloggingplatformi.dao.DaoFactory;
import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.MongoProvider;
import org.example.bloggingplatformi.model.User;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Session;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.controller.AuthorsController;
import org.example.bloggingplatformi.controller.DashboardController;
import org.example.bloggingplatformi.controller.LoginController;
import org.example.bloggingplatformi.controller.ModerationController;
import org.example.bloggingplatformi.controller.PostListController;
import org.example.bloggingplatformi.controller.TagsController;

/**
 * Starts the JavaFX app. The window opens on the sign in screen.
 * After a successful sign in the main window (AppShell) replaces it,
 * and "Sign out" brings the sign in screen back.
 */
public class BloggingPlatformApplication extends Application {

    /** Window size on a large screen. On a small screen the window is made smaller. */
    private static final double PREFERRED_WIDTH = 1340;
    private static final double PREFERRED_HEIGHT = 840;

    /**
     * Smallest size where the layout still works. It is kept small enough to
     * fit a 1280x720 screen once the taskbar is taken away.
     */
    private static final double MIN_WIDTH = 900;
    private static final double MIN_HEIGHT = 560;

    /** Space kept between the window and the screen edge. */
    private static final double SCREEN_MARGIN = 40;

    private Scene scene;
    private LoginController loginController;
    private AppShell shell;

    @Override
    public void start(Stage stage) {
        loginController = new LoginController(this::signIn);

        // getVisualBounds leaves out the taskbar, so the window fits the free space.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(PREFERRED_WIDTH, screen.getWidth() - SCREEN_MARGIN);
        double height = Math.min(PREFERRED_HEIGHT, screen.getHeight() - SCREEN_MARGIN);

        scene = new Scene(loginController.getRoot(), width, height);
        Styles.apply(scene);

        stage.setTitle("Blogfold — Smart Blogging Platform");
        stage.setScene(scene);
        // The minimum size is also limited to the screen size, so the window always fits.
        stage.setMinWidth(Math.min(MIN_WIDTH, screen.getWidth()));
        stage.setMinHeight(Math.min(MIN_HEIGHT, screen.getHeight()));
        stage.centerOnScreen();
        stage.show();
    }

    /** Called by the sign in screen with the user who signed in or signed up. */
    private void signIn(User user) {
        Session.setCurrentUser(user);

        shell = new AppShell(this::signOut);
        shell.register(AppShell.Destination.DASHBOARD, () -> new DashboardController(shell));
        shell.register(AppShell.Destination.POSTS, () -> new PostListController(shell));
        shell.register(AppShell.Destination.MODERATION, () -> new ModerationController(shell));
        shell.register(AppShell.Destination.AUTHORS, () -> new AuthorsController(shell));
        shell.register(AppShell.Destination.TAGS, () -> new TagsController());

        scene.setRoot(shell.getRoot());
        shell.navigate(AppShell.Destination.DASHBOARD);
        checkMongo();
    }

    /** Forgets the current user and goes back to the sign in screen. */
    private void signOut() {
        Session.setCurrentUser(null);
        shell = null;
        scene.setRoot(loginController.getRoot());
        loginController.showSignIn();
    }

    /**
     * Checks MongoDB on its own, because it can be down while PostgreSQL works.
     * Then posts still work but comments do not.
     */
    private void checkMongo() {
        if (DaoFactory.commentStore() != DaoFactory.CommentStore.MONGO) {
            return;
        }
        Async.run(MongoProvider::isReachable, reachable -> {
            if (!reachable) {
                shell.showBanner("Cannot reach MongoDB",
                        "Posts will work, but comments and reviews are unavailable. "
                        + "Start mongod, or set app.comments.store=POSTGRES in "
                        + "application.properties to use the relational store instead.",
                        true);
            }
        }, error -> { /* nothing to do, the banner above covers this case */ });
    }

    @Override
    public void stop() {
        // Close the database connections, or the JVM keeps running after the window closes.
        if (Database.isInitialised()) {
            Database.shutdown();
        }
        if (MongoProvider.isInitialised()) {
            MongoProvider.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
