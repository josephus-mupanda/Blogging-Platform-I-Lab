package org.example.bloggingplatformi.ui;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.bloggingplatformi.model.CommentStatus;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.Role;

/**
 * Shared view helpers: stylesheet wiring, the small building blocks every
 * screen reuses, and the formatters that keep dates and numbers consistent.
 *
 * <p>Keeping badge and avatar construction here rather than in each view is
 * what stops the same concept being drawn three slightly different ways.
 */
public final class Styles {

    public static final String STYLESHEET = "/org/example/bloggingplatformi/css/app.css";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private Styles() {
    }

    // -----------------------------------------------------------------
    // Stylesheet
    // -----------------------------------------------------------------

    public static void apply(Scene scene) {
        String url = resolve();
        if (url != null && !scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(url);
        }
    }

    /** Dialogs have their own Parent and do not inherit the scene's sheet. */
    public static void apply(Parent parent) {
        String url = resolve();
        if (url != null && !parent.getStylesheets().contains(url)) {
            parent.getStylesheets().add(url);
        }
    }

    private static String resolve() {
        URL url = Styles.class.getResource(STYLESHEET);
        if (url == null) {
            // Not fatal: the application is perfectly usable unstyled, and
            // failing to start over a missing stylesheet would be worse.
            System.err.println("Stylesheet not found on the classpath: " + STYLESHEET);
            return null;
        }
        return url.toExternalForm();
    }

    // -----------------------------------------------------------------
    // Building blocks
    // -----------------------------------------------------------------

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Label title(String text) {
        return label(text, "text-title");
    }

    public static Label subtitle(String text) {
        return label(text, "text-subtitle");
    }

    public static Label muted(String text) {
        return label(text, "text-muted");
    }

    /**
     * A message shown inside a panel: an empty state, or an error.
     *
     * <p>Always wrapping. An unwrapped label takes its full single-line
     * preferred width, which for a multi-sentence error is wider than the
     * panel, so the panel grows, its row overflows and the window gains a
     * horizontal scrollbar. {@code USE_PREF_SIZE} then stops the wrapped
     * height being clipped back to one line.
     */
    public static Label message(String text) {
        Label label = label(text, "text-small");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    public static Label eyebrow(String text) {
        return label(text.toUpperCase(Locale.ENGLISH), "text-eyebrow");
    }

    /** A horizontal spacer that pushes whatever follows it to the right. */
    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static Region verticalSpacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        return box;
    }

    /** A circle of initials. Cheap, always available, and never a broken image. */
    public static StackPane avatar(String name, boolean small) {
        StackPane pane = new StackPane(new Label(initials(name)));
        pane.getStyleClass().add("avatar");
        if (small) {
            pane.getStyleClass().add("avatar-sm");
        }
        return pane;
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.strip().split("[\\s_.-]+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ENGLISH);
        }
        return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ENGLISH);
    }

    // -----------------------------------------------------------------
    // Badges
    // -----------------------------------------------------------------

    public static Label badge(String text, String variant) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", "badge-" + variant);
        return badge;
    }

    /** Colour carries the meaning: green is live, grey is not yet, amber is retired. */
    public static Label statusBadge(PostStatus status) {
        return switch (status) {
            case PUBLISHED -> badge("Published", "success");
            case DRAFT -> badge("Draft", "neutral");
            case ARCHIVED -> badge("Archived", "warning");
        };
    }

    public static Label statusBadge(CommentStatus status) {
        return switch (status) {
            case VISIBLE -> badge("Visible", "success");
            case HIDDEN -> badge("Hidden", "neutral");
            case FLAGGED -> badge("Flagged", "danger");
        };
    }

    public static Label roleBadge(Role role) {
        return switch (role) {
            case ADMIN -> badge("Admin", "info");
            case AUTHOR -> badge("Author", "neutral");
            case READER -> badge("Reader", "neutral");
        };
    }

    public static Label chip(String text) {
        return label(text, "chip");
    }

    // -----------------------------------------------------------------
    // Formatting
    // -----------------------------------------------------------------

    public static String formatDate(OffsetDateTime value) {
        return value == null ? "—" : value.atZoneSameInstant(ZoneId.systemDefault()).format(DATE);
    }

    public static String formatDateTime(OffsetDateTime value) {
        return value == null ? "—"
                : value.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME);
    }

    /**
     * "3 days ago" reads faster than a date in an activity feed, but an exact
     * date is better once something is older than a month.
     */
    public static String relative(OffsetDateTime value) {
        if (value == null) {
            return "—";
        }
        long minutes = java.time.Duration.between(value, OffsetDateTime.now()).toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        if (days < 31) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        return formatDate(value);
    }

    /** Thousands separators, so 12 847 does not read as 1 284 7. */
    public static String number(long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    public static Insets insets(double top, double right, double bottom, double left) {
        return new Insets(top, right, bottom, left);
    }
}
