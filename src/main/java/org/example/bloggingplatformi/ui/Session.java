package org.example.bloggingplatformi.ui;

import java.util.Optional;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;

/**
 * Who is currently acting in the application.
 *
 * <p>This is a user *switcher*, not authentication. Password verification is
 * explicitly out of scope for this stage of the project, and pretending
 * otherwise by checking a hash the seed data does not really contain would be
 * worse than being clear about it: the UI needs an author to attribute posts
 * and comments to, and this supplies one.
 *
 * <p>Held as an observable property so the header and the permission-sensitive
 * controls update themselves when the acting user changes.
 */
public final class Session {

    private static final ObjectProperty<User> CURRENT = new SimpleObjectProperty<>();

    private Session() {
    }

    public static ObjectProperty<User> currentUserProperty() {
        return CURRENT;
    }

    public static Optional<User> currentUser() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void setCurrentUser(User user) {
        CURRENT.set(user);
    }

    public static boolean isSignedIn() {
        return CURRENT.get() != null;
    }

    /** The acting user's id, or 0 when nobody is selected. */
    public static long currentUserId() {
        User user = CURRENT.get();
        return user == null ? 0L : user.userId();
    }

    public static String currentDisplayName() {
        User user = CURRENT.get();
        return user == null ? "No user selected" : user.displayName();
    }

    public static Role currentRole() {
        User user = CURRENT.get();
        return user == null ? Role.READER : user.role();
    }

    /** Readers may comment and review; they may not author posts. */
    public static boolean canPublish() {
        return isSignedIn() && currentRole().canPublish();
    }

    /** Only an admin sees the moderation queue. */
    public static boolean canModerate() {
        return isSignedIn() && currentRole().canModerate();
    }
}
