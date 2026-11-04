package org.example.bloggingplatformi.ui;

import org.example.bloggingplatformi.model.User;

/** Remembers who is signed in. Set by the sign in screen, cleared by "Sign out". */
public final class Session {

    private static User currentUser;

    private Session() {
    }

    public static User user() {
        return currentUser;
    }

    public static void signIn(User user) {
        currentUser = user;
    }

    public static void signOut() {
        currentUser = null;
    }

    /** Bloggers (AUTHOR) and the admin can write posts. */
    public static boolean canWritePosts() {
        return currentUser != null && currentUser.role().canWritePosts();
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.role().isAdmin();
    }

    /** A post can be edited or deleted by its author, or by the admin. */
    public static boolean canEditPost(long authorId) {
        return currentUser != null && (currentUser.id() == authorId || isAdmin());
    }
}
