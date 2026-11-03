package org.example.bloggingplatformi.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * Loads a screen layout from an .fxml file in src/main/resources/org/example/bloggingplatformi/view/.
 *
 * <p>The .fxml file describes what is on the screen (the view). The controller object is
 * the Java class that reacts to clicks and loads data (the controller). FXMLLoader fills
 * the controller's {@code @FXML} fields with the nodes that have the same {@code fx:id},
 * then calls its {@code initialize()} method.
 */
public final class Fxml {

    private static final String FOLDER = "/org/example/bloggingplatformi/view/";

    private Fxml() {
    }

    /**
     * @param file       the file name, for example "login.fxml"
     * @param controller the object named by {@code fx:controller} in the file. We pass our own
     *                   object because the controllers need constructor arguments (the shell,
     *                   a post id), which FXMLLoader cannot give them.
     * @return the root node of the layout
     */
    public static <T extends Parent> T load(String file, Object controller) {
        URL url = Fxml.class.getResource(FOLDER + file);
        if (url == null) {
            throw new IllegalStateException("FXML file not found: " + FOLDER + file);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(type -> controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + file, e);
        }
    }
}
