package org.example.bloggingplatformi.ui;

import java.util.List;

import javafx.scene.Node;

/**
 * One screen in the application.
 *
 * <p>A view owns its content and tells the shell what to put in the header.
 * It does not own the header, the navigation or the window, which is what lets
 * every screen share one layout without copying it.
 */
public interface View {

    /** The content node. Built once, in the constructor. */
    Node getRoot();

    /** Shown as the page heading in the top bar. */
    String title();

    /** Optional line under the heading; null for none. */
    default String subtitle() {
        return null;
    }

    /** Buttons placed on the right of the top bar, in order. */
    default List<Node> actions() {
        return List.of();
    }

    /**
     * Called every time the view is displayed, including on a return from
     * another screen.
     *
     * <p>Data loading belongs here rather than in the constructor, so coming
     * back to a list shows what the database holds now rather than what it
     * held when the screen was first opened.
     */
    default void onShow() {
    }
}
