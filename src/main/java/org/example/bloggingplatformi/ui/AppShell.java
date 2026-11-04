package org.example.bloggingplatformi.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.example.bloggingplatformi.model.User;

/**
 * Controller for main.fxml: the main window shown after sign in.
 * It has a sidebar menu, a top bar for the current screen, and a content area
 * that swaps between screens. It also handles navigation and the back button.
 */
public final class AppShell {

    /**
     * The sidebar items. The "Manage" items are for the admin only;
     * other users do not see them at all.
     */
    public enum Destination {
        DASHBOARD(false),
        POSTS(false),
        MODERATION(true),
        AUTHORS(true),
        TAGS(true);

        final boolean adminOnly;

        Destination(boolean adminOnly) {
            this.adminOnly = adminOnly;
        }

        /** True if the signed-in user may open this screen. */
        boolean isAllowed() {
            return !adminOnly || Session.canModerate();
        }
    }

    private final BorderPane root;
    private final Runnable onSignOut;

    // Filled by FXMLLoader from the fx:id values in main.fxml.
    @FXML private Button dashboardNav;
    @FXML private Button postsNav;
    @FXML private VBox manageSection;
    @FXML private Button moderationNav;
    @FXML private Button authorsNav;
    @FXML private Button tagsNav;
    @FXML private Button backButton;
    @FXML private Label headingLabel;
    @FXML private Label subheadingLabel;
    @FXML private HBox actionBar;
    @FXML private StackPane avatarSlot;
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private VBox bannerSlot;
    @FXML private StackPane contentHost;

    private final Map<Destination, Supplier<View>> routes = new LinkedHashMap<>();
    private final Map<Destination, Button> navButtons = new LinkedHashMap<>();

    /**
     * Views shown before, for the back button.
     * The same objects are kept, so going back keeps the user's filters and page.
     */
    private final Deque<View> history = new ArrayDeque<>();

    private View currentView;
    private Destination currentDestination;

    /**
     * Builds the main window for the user who is signed in ({@link Session}).
     * A new shell is made after every sign in, so nothing from the last user is kept.
     *
     * @param onSignOut called when the user confirms "Sign out"
     */
    public AppShell(Runnable onSignOut) {
        this.onSignOut = onSignOut;
        this.root = Fxml.load("main.fxml", this);
    }

    /** Called by FXMLLoader once the @FXML fields are filled. */
    @FXML
    private void initialize() {
        navButtons.put(Destination.DASHBOARD, dashboardNav);
        navButtons.put(Destination.POSTS, postsNav);
        navButtons.put(Destination.MODERATION, moderationNav);
        navButtons.put(Destination.AUTHORS, authorsNav);
        navButtons.put(Destination.TAGS, tagsNav);

        // Icons are SVG paths kept in Icons.java, so they are added here and not in the FXML.
        dashboardNav.setGraphic(Icons.of(Icons.DASHBOARD, 17));
        postsNav.setGraphic(Icons.of(Icons.ARTICLE, 17));
        moderationNav.setGraphic(Icons.of(Icons.SHIELD, 17));
        authorsNav.setGraphic(Icons.of(Icons.PERSON, 17));
        tagsNav.setGraphic(Icons.of(Icons.TAG, 17));
        backButton.setGraphic(Icons.of(Icons.BACK, 17));
        backButton.setTooltip(tooltip("Back"));

        // The Manage section is not shown at all to bloggers and readers.
        boolean admin = Session.canModerate();
        manageSection.setVisible(admin);
        manageSection.setManaged(admin);

        // Who is signed in, in the top-right corner.
        User user = Session.currentUser().orElseThrow();
        avatarSlot.getChildren().setAll(Styles.avatar(user.displayName(), true));
        userNameLabel.setText(user.displayName());
        userRoleLabel.setText(user.role().name().toLowerCase());
    }

    public BorderPane getRoot() {
        return root;
    }

    public void register(Destination destination, Supplier<View> factory) {
        routes.put(destination, factory);
    }

    // -----------------------------------------------------------------
    // Menu and top bar buttons (onAction="#..." in main.fxml)
    // -----------------------------------------------------------------

    @FXML private void openDashboard() { navigate(Destination.DASHBOARD); }

    @FXML private void openPosts() { navigate(Destination.POSTS); }

    @FXML private void openModeration() { navigate(Destination.MODERATION); }

    @FXML private void openAuthors() { navigate(Destination.AUTHORS); }

    @FXML private void openTags() { navigate(Destination.TAGS); }

    @FXML
    private void signOut() {
        String name = Session.currentDisplayName();
        if (Dialogs.confirm("Sign out", "Do you want to sign out, " + name + "?", "Sign out")) {
            onSignOut.run();
        }
    }

    // -----------------------------------------------------------------
    // Banner
    // -----------------------------------------------------------------

    /** Shows a message above the content that stays until cleared, such as a lost connection. */
    public void showBanner(String headline, String detail, boolean isError) {
        Label title = Styles.label(headline, "text-subtitle");
        Label body = Styles.label(detail, "text-small");
        body.setWrapText(true);
        title.setWrapText(true);

        // Let the text grow to the available width, so a long message wraps
        // instead of making the window scroll sideways.
        VBox text = new VBox(2, title, body);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox banner = new HBox(12, Icons.of(Icons.DATABASE, 18), text);
        banner.setMaxWidth(Double.MAX_VALUE);

        // USE_PREF_SIZE stops the wrapped text being cut down to one line.
        title.setMinHeight(Region.USE_PREF_SIZE);
        body.setMinHeight(Region.USE_PREF_SIZE);
        text.setMinHeight(Region.USE_PREF_SIZE);
        banner.setMinHeight(Region.USE_PREF_SIZE);
        bannerSlot.setMinHeight(Region.USE_PREF_SIZE);

        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add(isError ? "banner-error" : "banner-info");

        bannerSlot.getChildren().setAll(banner);
        bannerSlot.setVisible(true);
        bannerSlot.setManaged(true);
    }

    // -----------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------

    /** Switches to a top-level destination, clearing the back history. */
    public void navigate(Destination destination) {
        Supplier<View> factory = routes.get(destination);
        if (factory == null || !destination.isAllowed()) {
            return;
        }
        history.clear();
        currentDestination = destination;
        highlight(destination);
        show(factory.get(), false);
    }

    /** Opens a nested screen, such as a post detail, with a back button. */
    public void push(View view) {
        if (currentView != null) {
            history.push(currentView);
        }
        show(view, true);
    }

    @FXML
    public void back() {
        if (history.isEmpty()) {
            if (currentDestination != null) {
                navigate(currentDestination);
            }
            return;
        }
        show(history.pop(), !history.isEmpty());
    }

    /**
     * Updates the heading if {@code view} is still on screen.
     * Used when the title depends on data that loads later, like a post title.
     */
    public void refreshHeading(View view) {
        if (view == currentView) {
            applyHeading(view);
            // The buttons can depend on the loaded data too (for example, who wrote the post).
            actionBar.getChildren().setAll(view.actions());
        }
    }

    private void applyHeading(View view) {
        headingLabel.setText(view.title());

        String subtitle = view.subtitle();
        boolean hasSubtitle = subtitle != null && !subtitle.isBlank();
        subheadingLabel.setText(hasSubtitle ? subtitle : "");
        subheadingLabel.setVisible(hasSubtitle);
        subheadingLabel.setManaged(hasSubtitle);
    }

    private void show(View view, boolean showBack) {
        currentView = view;

        applyHeading(view);

        actionBar.getChildren().setAll(view.actions());

        backButton.setVisible(showBack);
        backButton.setManaged(showBack);

        contentHost.getChildren().setAll(view.getRoot());

        // Called after the view is added, so it can load data and set focus.
        view.onShow();
    }

    private void highlight(Destination destination) {
        navButtons.forEach((key, button) -> {
            button.getStyleClass().remove("active");
            if (key == destination) {
                button.getStyleClass().add("active");
            }
        });
    }

    // -----------------------------------------------------------------
    // Small helpers
    // -----------------------------------------------------------------

    /** Creates a tooltip. The views use it too. */
    public static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        // The default delay is 1.5 seconds, which feels too slow.
        tooltip.setShowDelay(Duration.millis(350));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(260);
        return tooltip;
    }

    /** Public so views can add a button like "Go to posts" to an empty screen. */
    public Node buildPrimaryButton(String text, String icon, Runnable action) {
        Button button = new Button(text);
        button.setGraphic(Icons.of(icon, 16));
        button.getStyleClass().add("btn-primary");
        button.setOnAction(event -> action.run());
        return button;
    }
}
