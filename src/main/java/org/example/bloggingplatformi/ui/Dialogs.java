package org.example.bloggingplatformi.ui;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** Small pop-up windows: an error message, or a yes / no question. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Something went wrong");
        alert.showAndWait();
    }

    /** Returns true if the user clicked OK. */
    public static boolean confirm(String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question);
        alert.setHeaderText(null);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }
}
