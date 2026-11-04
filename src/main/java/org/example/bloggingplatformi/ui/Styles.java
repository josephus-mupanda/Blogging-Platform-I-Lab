package org.example.bloggingplatformi.ui;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
 * Shared helpers for the views: loading the stylesheet, small UI building blocks
 * like labels and badges, and date and number formatting.
 * Keeping them in one place makes every screen look the same.
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

    /** Dialogs do not get the scene's stylesheet, so it is added to them here. */
    public static void apply(Parent parent) {
        String url = resolve();
        if (url != null && !parent.getStylesheets().contains(url)) {
            parent.getStylesheets().add(url);
        }
    }

    private static String resolve() {
        URL url = Styles.class.getResource(STYLESHEET);
        if (url == null) {
            // The app still works without styles, so we only log this.
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

    /**
     * A message shown inside a panel, such as an empty state or an error.
     * The text wraps so a long message does not make the panel too wide.
     */
    public static Label message(String text) {
        Label label = label(text, "text-small");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    /** A horizontal spacer that pushes whatever follows it to the right. */
    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    /**
     * A horizontal bar filled to {@code ratio} of its width.
     * The fill's max width is bound to the track width, because a StackPane
     * would otherwise stretch the fill to full width.
     *
     * @param ratio     a value from 0 to 1; NaN counts as 0
     * @param prefWidth the preferred width of the bar
     */
    public static StackPane proportionBar(double ratio, double prefWidth) {
        double clamped = Double.isNaN(ratio) ? 0 : Math.max(0, Math.min(1, ratio));

        Region fill = new Region();
        fill.getStyleClass().add("rating-bar-fill");

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("rating-bar-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setPrefWidth(prefWidth);
        track.setMinWidth(40);
        HBox.setHgrow(track, Priority.ALWAYS);

        // Zero shows no fill. Any value above zero gets at least 4 pixels so it stays visible.
        fill.setVisible(clamped > 0);
        fill.setMinWidth(0);
        fill.maxWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> clamped == 0 ? 0 : Math.max(4, track.getWidth() * clamped),
                track.widthProperty()));
        return track;
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    /** A circle with the user's initials, used instead of a profile picture. */
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

    /** Green for published, grey for draft, amber for archived. */
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

    /** Formats a time like "3 days ago". Anything older than a month shows the date instead. */
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

    /** Formats a number with thousands separators, for example 12,847. */
    public static String number(long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }
}
