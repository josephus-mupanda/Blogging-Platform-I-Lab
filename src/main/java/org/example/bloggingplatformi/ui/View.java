package org.example.bloggingplatformi.ui;

import java.util.List;

import javafx.scene.Node;

/**
 * One screen in the application.
 * A view builds its own content and tells the shell what to show in the header.
 */
public interface View {

    /** The content node. It is built once, in the constructor. */
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
     * Called every time the view is shown, including when coming back from another screen.
     * Data is loaded here, not in the constructor, so the screen always shows fresh data.
     */
    default void onShow() {
    }
}
