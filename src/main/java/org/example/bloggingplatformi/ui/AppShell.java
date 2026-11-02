package org.example.bloggingplatformi.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
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
 * The application frame: a fixed sidebar, a top bar that reflects the current
 * screen, and a content host that swaps views.
 *
 * <p>Keeping navigation, the heading and the action bar here means a screen
 * only has to describe itself through {@link View}. Adding one is a matter of
 * registering a supplier, not re-creating a layout.
 */
public final class AppShell {

    /** Sidebar destinations, in display order within their group. */
    public enum Destination {
        DASHBOARD("Dashboard", Icons.DASHBOARD, "Workspace"),
        POSTS("Posts", Icons.ARTICLE, "Workspace"),
        MODERATION("Moderation", Icons.SHIELD, "Manage"),
        AUTHORS("Authors", Icons.PERSON, "Manage"),
        TAGS("Tags", Icons.TAG, "Manage");

        final String label;
        final String icon;
        final String group;

        Destination(String label, String icon, String group) {
            this.label = label;
            this.icon = icon;
            this.group = group;
        }
    }

    private final BorderPane root = new BorderPane();
    private final StackPane contentHost = new StackPane();
    private final VBox navItems = new VBox(3);

    private final Label headingLabel = Styles.label("", "text-title");
    private final Label subheadingLabel = Styles.label("", "text-small");
    private final VBox headingBox = new VBox(1, headingLabel, subheadingLabel);
    private final HBox actionBar = new HBox(9);

    private final Map<Destination, Supplier<View>> routes = new LinkedHashMap<>();
    private final Map<Destination, Button> navButtons = new LinkedHashMap<>();

    /**
     * Previously shown views, for the back button.
     *
     * <p>Instances are kept rather than rebuilt, so returning to a list
     * restores the filters and page the user left it on. {@link View#onShow()}
     * refreshes the data, so stale rows are not the price of that.
     */
    private final Deque<View> history = new ArrayDeque<>();

    private View currentView;
    private Destination currentDestination;
    private final Button backButton;
    private final ComboBox<User> userSwitcher = new ComboBox<>();
    private final VBox bannerSlot = new VBox();

    public AppShell() {
        backButton = iconButton(Icons.BACK, "Back", "btn-ghost");
        backButton.setOnAction(event -> back());
        backButton.setVisible(false);
        backButton.setManaged(false);

        root.setLeft(buildSidebar());
        root.setCenter(buildMain());
    }

    public BorderPane getRoot() {
        return root;
    }

    public void register(Destination destination, Supplier<View> factory) {
        routes.put(destination, factory);
    }

    // -----------------------------------------------------------------
    // Sidebar
    // -----------------------------------------------------------------

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        sidebar.getChildren().add(buildBrand());

        String lastGroup = null;
        VBox group = new VBox(3);
        group.setPadding(new Insets(0, 12, 0, 12));

        for (Destination destination : Destination.values()) {
            if (!destination.group.equals(lastGroup)) {
                Label sectionLabel = new Label(destination.group.toUpperCase());
                sectionLabel.getStyleClass().add("sidebar-section-label");
                group.getChildren().add(sectionLabel);
                lastGroup = destination.group;
            }
            Button button = buildNavButton(destination);
            navButtons.put(destination, button);
            group.getChildren().add(button);
        }

        navItems.getChildren().add(group);
        sidebar.getChildren().add(navItems);
        sidebar.getChildren().add(Styles.verticalSpacer());
        sidebar.getChildren().add(buildSidebarFooter());

        return sidebar;
    }

    private HBox buildBrand() {
        StackPane mark = new StackPane(new Label("B"));
        mark.getStyleClass().add("sidebar-brand-mark");

        Label name = Styles.label("Blogfold", "sidebar-brand-title");
        Label tagline = Styles.label("Content workspace", "sidebar-brand-subtitle");

        HBox brand = new HBox(11, mark, new VBox(0, name, tagline));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("sidebar-brand");
        return brand;
    }

    private Button buildNavButton(Destination destination) {
        Button button = new Button(destination.label);
        button.setGraphic(Icons.of(destination.icon, 17));
        button.getStyleClass().add("nav-item");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> navigate(destination));
        return button;
    }

    private VBox buildSidebarFooter() {
        Label store = Styles.label("", "sidebar-brand-subtitle");
        store.setText("Comments store: " + storeLabel());

        HBox line = new HBox(8, Icons.of(Icons.DATABASE, 14), store);
        line.setAlignment(Pos.CENTER_LEFT);

        VBox footer = new VBox(6, line);
        footer.getStyleClass().add("sidebar-footer");
        return footer;
    }

    private String storeLabel() {
        try {
            return org.example.bloggingplatformi.dao.DaoFactory.commentStore().name();
        } catch (RuntimeException e) {
            // Configuration problems must not stop the window from opening.
            return "unknown";
        }
    }

    // -----------------------------------------------------------------
    // Main area
    // -----------------------------------------------------------------

    private VBox buildMain() {
        VBox main = new VBox(buildTopBar(), bannerSlot, contentHost);
        main.getStyleClass().add("content-area");
        VBox.setVgrow(contentHost, Priority.ALWAYS);

        bannerSlot.setPadding(new Insets(16, 26, 0, 26));
        bannerSlot.setVisible(false);
        bannerSlot.setManaged(false);

        return main;
    }

    private HBox buildTopBar() {
        subheadingLabel.setVisible(false);
        subheadingLabel.setManaged(false);
        headingBox.setAlignment(Pos.CENTER_LEFT);

        configureUserSwitcher();

        HBox topBar = new HBox(12, backButton, headingBox, Styles.spacer(), actionBar,
                buildUserArea());
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("topbar");
        actionBar.setAlignment(Pos.CENTER_RIGHT);
        return topBar;
    }

    private HBox buildUserArea() {
        HBox area = new HBox(9, userSwitcher);
        area.setAlignment(Pos.CENTER_RIGHT);
        area.setPadding(new Insets(0, 0, 0, 10));
        return area;
    }

    private void configureUserSwitcher() {
        userSwitcher.setPrefWidth(210);
        userSwitcher.setPromptText("Select a user");
        userSwitcher.setButtonCell(userCell());
        userSwitcher.setCellFactory(list -> userCell());
        userSwitcher.setTooltip(tooltip(
                "Acts as this user when creating posts, comments and reviews."));

        userSwitcher.valueProperty().addListener((observable, previous, selected) -> {
            if (selected != null) {
                Session.setCurrentUser(selected);
                // Permission-sensitive items (the moderation entry) depend on
                // the acting user's role, so the sidebar is re-evaluated.
                refreshPermissions();
            }
        });
    }

    private ListCell<User> userCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = Styles.label(user.displayName(), "text-body");
                Label role = Styles.label(user.role().name().toLowerCase(), "text-caption");
                HBox cell = new HBox(8, Styles.avatar(user.displayName(), true),
                        new VBox(0, name, role));
                cell.setAlignment(Pos.CENTER_LEFT);
                setGraphic(cell);
                setText(null);
            }
        };
    }

    public void setUsers(List<User> users) {
        userSwitcher.getItems().setAll(users);
        if (!users.isEmpty() && userSwitcher.getValue() == null) {
            // Prefer an account that can actually author posts, so the app
            // opens in a state where the primary action works.
            User preferred = users.stream()
                    .filter(user -> user.role().canModerate())
                    .findFirst()
                    .orElse(users.get(0));
            userSwitcher.setValue(preferred);
        }
    }

    private void refreshPermissions() {
        Button moderation = navButtons.get(Destination.MODERATION);
        if (moderation != null) {
            boolean allowed = Session.canModerate();
            moderation.setDisable(!allowed);
            moderation.setTooltip(allowed ? null
                    : tooltip("Only an admin can open the moderation queue."));
        }
    }

    // -----------------------------------------------------------------
    // Banner
    // -----------------------------------------------------------------

    /** Shows a persistent message above the content, such as a lost connection. */
    public void showBanner(String headline, String detail, boolean isError) {
        Label title = Styles.label(headline, "text-subtitle");
        Label body = Styles.label(detail, "text-small");
        body.setWrapText(true);
        title.setWrapText(true);

        // Without the grow constraint the VBox takes its preferred width,
        // which for a long message is wider than the window, and the banner
        // pushes the whole content area into a horizontal scroll.
        VBox text = new VBox(2, title, body);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox banner = new HBox(12, Icons.of(Icons.DATABASE, 18), text);
        banner.setMaxWidth(Double.MAX_VALUE);

        // A wrapped Label nested inside VBoxes is measured at its single-line
        // height and then clipped, which is what reduced the whole message to
        // one ellipsised line. USE_PREF_SIZE makes every container along the
        // way honour the wrapped height instead.
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

    public void clearBanner() {
        bannerSlot.getChildren().clear();
        bannerSlot.setVisible(false);
        bannerSlot.setManaged(false);
    }

    // -----------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------

    /** Switches to a top-level destination, clearing the back history. */
    public void navigate(Destination destination) {
        Supplier<View> factory = routes.get(destination);
        if (factory == null) {
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
     * Re-reads the heading of {@code view} if it is still the one on screen.
     *
     * <p>For views whose title depends on data they load, such as a post's
     * detail screen: the heading is first set before that data arrives.
     */
    public void refreshHeading(View view) {
        if (view == currentView) {
            applyHeading(view);
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

        // After the node is in the scene, so a view can measure or focus.
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

    private Button iconButton(String icon, String tooltipText, String styleClass) {
        Button button = new Button();
        button.setGraphic(Icons.of(icon, 17));
        button.getStyleClass().addAll(styleClass, "btn-small");
        button.setTooltip(tooltip(tooltipText));
        return button;
    }

    /** Shared tooltip factory; the views in the {@code view} package use it too. */
    public static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        // The platform default is a second and a half, which is long enough
        // that people assume there is no tooltip.
        tooltip.setShowDelay(Duration.millis(350));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(260);
        return tooltip;
    }

    /** Exposed so views can offer a "go to posts" action from an empty state. */
    public Node buildPrimaryButton(String text, String icon, Runnable action) {
        Button button = new Button(text);
        button.setGraphic(Icons.of(icon, 16));
        button.getStyleClass().add("btn-primary");
        button.setOnAction(event -> action.run());
        return button;
    }
}
