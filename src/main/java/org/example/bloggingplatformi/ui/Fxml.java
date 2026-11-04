package org.example.bloggingplatformi.ui;

import java.io.IOException;
import java.io.UncheckedIOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * Loads a screen from an .fxml file in src/main/resources/org/example/bloggingplatformi/view/.
 *
 * The .fxml file says what is on the screen. The controller is the Java object that reacts
 * to the clicks. FXMLLoader fills the controller's @FXML fields (same name as the fx:id in
 * the file) and calls its initialize() method.
 */
public final class Fxml {

    private Fxml() {
    }

    /**
     * We give FXMLLoader our own controller object (instead of letting it create one),
     * because our controllers need a constructor argument, for example the post id.
     */
    public static Parent load(String fileName, Object controller) {
        FXMLLoader loader = new FXMLLoader(Fxml.class.getResource("/org/example/bloggingplatformi/view/" + fileName));
        loader.setControllerFactory(type -> controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + fileName, e);
        }
    }
}
