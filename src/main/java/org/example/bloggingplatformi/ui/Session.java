package org.example.bloggingplatformi.ui;

import java.util.Optional;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.example.bloggingplatformi.model.Role;
import org.example.bloggingplatformi.model.User;

/**
 * Keeps track of the user who is signed in.
 * It is set by the sign in screen and cleared by "Sign out".
 * The views ask it what the current user is allowed to do.
 */
public final class Session {

    private static final ObjectProperty<User> CURRENT = new SimpleObjectProperty<>();

    private Session() {
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

    /** The signed-in user's id, or 0 when nobody is signed in. */
    public static long currentUserId() {
        User user = CURRENT.get();
        return user == null ? 0L : user.userId();
    }

    public static String currentDisplayName() {
        User user = CURRENT.get();
        return user == null ? "Not signed in" : user.displayName();
    }

    public static Role currentRole() {
        User user = CURRENT.get();
        return user == null ? Role.READER : user.role();
    }

    /** Readers may comment and review; they may not author posts. */
    public static boolean canPublish() {
        return isSignedIn() && currentRole().canPublish();
    }

    /** Only an admin sees the moderation queue and the Manage screens. */
    public static boolean canModerate() {
        return isSignedIn() && currentRole().canModerate();
    }

    /** A post can be edited or deleted by its own author, or by an admin. */
    public static boolean canEditPost(long postAuthorId) {
        return canModerate() || (canPublish() && postAuthorId == currentUserId());
    }
}
