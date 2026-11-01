package org.example.bloggingplatformi;

import java.util.List;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import org.example.bloggingplatformi.db.Database;
import org.example.bloggingplatformi.db.MongoProvider;
import org.example.bloggingplatformi.service.UserService;
import org.example.bloggingplatformi.ui.AppShell;
import org.example.bloggingplatformi.ui.Async;
import org.example.bloggingplatformi.ui.Styles;
import org.example.bloggingplatformi.view.AuthorsView;
import org.example.bloggingplatformi.view.DashboardView;
import org.example.bloggingplatformi.view.ModerationView;
import org.example.bloggingplatformi.view.PostListView;
import org.example.bloggingplatformi.view.TagsView;

/**
 * Application entry point.
 *
 * <p>The window opens before any database work is attempted, and the first
 * query runs in the background. A desktop application that blanks the screen
 * while it waits on a connection timeout looks broken, and the one time that
 * matters most is when the database really is unreachable — exactly when the
 * user needs to be told what is wrong rather than shown nothing.
 */
public class BloggingPlatformApplication extends Application {

    /** Comfortable size on a large display; never used as-is on a small one. */
    private static final double PREFERRED_WIDTH = 1340;
    private static final double PREFERRED_HEIGHT = 840;

    /**
     * Floor for the layout to stay usable. Kept below 1280x672 on purpose:
     * a 1280x720 screen has roughly 672px of usable height once the taskbar
     * is removed, and a minimum taller than that produces a window the user
     * physically cannot fit on screen.
     */
    private static final double MIN_WIDTH = 900;
    private static final double MIN_HEIGHT = 560;

    /** Breathing room so the window never sits flush against the screen edge. */
    private static final double SCREEN_MARGIN = 40;

    private AppShell shell;

    @Override
    public void start(Stage stage) {
        shell = new AppShell();

        shell.register(AppShell.Destination.DASHBOARD, () -> new DashboardView(shell));
        shell.register(AppShell.Destination.POSTS, () -> new PostListView(shell));
        shell.register(AppShell.Destination.MODERATION, () -> new ModerationView(shell));
        shell.register(AppShell.Destination.AUTHORS, () -> new AuthorsView(shell));
        shell.register(AppShell.Destination.TAGS, () -> new TagsView(shell));

        // getVisualBounds, not getBounds: it already excludes the taskbar, so
        // the window is sized against the space actually available.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(PREFERRED_WIDTH, screen.getWidth() - SCREEN_MARGIN);
        double height = Math.min(PREFERRED_HEIGHT, screen.getHeight() - SCREEN_MARGIN);

        Scene scene = new Scene(shell.getRoot(), width, height);
        Styles.apply(scene);

        stage.setTitle("Blogfold — Smart Blogging Platform");
        stage.setScene(scene);
        // The minimum is itself clamped to the screen: on a small display a
        // hard-coded floor would make the window unresizable past the edge.
        stage.setMinWidth(Math.min(MIN_WIDTH, screen.getWidth()));
        stage.setMinHeight(Math.min(MIN_HEIGHT, screen.getHeight()));
        stage.centerOnScreen();
        stage.show();

        shell.navigate(startDestination());
        if ("EDITOR".equalsIgnoreCase(System.getenv("START_VIEW"))) {
            shell.push(new org.example.bloggingplatformi.view.PostEditorView(shell, null));
        }
        loadUsers();

    }

    /**
     * Loads the account list for the user switcher, and uses the attempt as
     * the connection probe.
     *
     * <p>Failing here is the most useful place to detect a configuration
     * problem: it happens once, at startup, and produces a banner naming the
     * two things worth checking instead of an error dialog per screen.
     */
    private void loadUsers() {
        UserService userService = new UserService();

        Async.run(() -> userService.findAll(200, 0),
                users -> {
                    shell.setUsers(users);
                    if (users.isEmpty()) {
                        shell.showBanner("No accounts found",
                                "The database is reachable but holds no users. Run "
                                + "db/postgres/V3__seed.sql to load the sample data.",
                                false);
                    } else {
                        shell.clearBanner();
                        checkMongo();
                    }
                },
                // humanise() already ends with the "check both servers and the
                // connection settings" guidance, so adding our own copy of it
                // printed the same advice to the user twice.
                error -> shell.showBanner("Cannot reach PostgreSQL",
                        org.example.bloggingplatformi.ui.Dialogs.humanise(error),
                        true));
    }

    /**
     * Probes MongoDB separately, because the two stores fail independently:
     * posts can work perfectly while comments do not.
     */
    private void checkMongo() {
        if (org.example.bloggingplatformi.dao.DaoFactory.commentStore()
                != org.example.bloggingplatformi.dao.DaoFactory.CommentStore.MONGO) {
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
        }, error -> { /* the banner above already covers an unreachable store */ });
    }

    /**
     * Opening screen, overridable with START_VIEW for screenshot capture.
     * Falls back to the dashboard for any unrecognised value.
     */
    private static AppShell.Destination startDestination() {
        String requested = System.getenv("START_VIEW");
        if (requested == null || requested.isBlank()) {
            return AppShell.Destination.DASHBOARD;
        }
        try {
            return AppShell.Destination.valueOf(requested.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AppShell.Destination.DASHBOARD;
        }
    }

    @Override
    public void stop() {
        // Hikari's threads are not daemons, so without this the JVM keeps
        // running after the window closes.
        Async.shutdown();
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
