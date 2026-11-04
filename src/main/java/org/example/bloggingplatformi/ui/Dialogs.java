package org.example.bloggingplatformi.ui;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

import org.example.bloggingplatformi.exception.DataAccessException;
import org.example.bloggingplatformi.exception.DuplicateEntityException;
import org.example.bloggingplatformi.exception.EntityNotFoundException;
import org.example.bloggingplatformi.exception.ValidationException;

/** Alert dialogs, styled to match the rest of the application. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(String title, Throwable error) {
        Async.onFxThread(() -> {
            Alert alert = build(Alert.AlertType.ERROR, title, humanise(error));
            alert.showAndWait();
        });
    }

    public static void error(String title, String message) {
        Async.onFxThread(() -> build(Alert.AlertType.ERROR, title, message).showAndWait());
    }

    /**
     * Asks the user to confirm an action that cannot be undone.
     * The confirm button shows the action name (for example "Delete post") instead of "OK".
     */
    public static boolean confirm(String title, String message, String confirmLabel) {
        ButtonType confirmButton = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = build(Alert.AlertType.CONFIRMATION, title, message);
        alert.getButtonTypes().setAll(cancelButton, confirmButton);

        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == confirmButton;
    }

    private static Alert build(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);

        // A Label wraps long text. The built-in content text does not, so the dialog would get too wide.
        Label content = new Label(message);
        content.setWrapText(true);
        content.setMaxWidth(430);

        DialogPane pane = alert.getDialogPane();
        pane.setContent(content);
        pane.setMinHeight(Region.USE_PREF_SIZE);
        Styles.apply(pane);

        return alert;
    }

    /**
     * Turns an exception into a message a user can understand.
     * Each exception type from the service layer gets its own message.
     */
    public static String humanise(Throwable error) {
        Throwable cause = unwrap(error);

        if (cause instanceof ValidationException validation) {
            StringBuilder message = new StringBuilder("Please fix the following:\n");
            for (Map.Entry<String, String> entry : validation.getErrors().entrySet()) {
                message.append("\n  •  ").append(entry.getValue());
            }
            return message.toString();
        }
        if (cause instanceof DuplicateEntityException duplicate) {
            return duplicate.getMessage();
        }
        if (cause instanceof EntityNotFoundException notFound) {
            return notFound.getMessage() + "\n\nIt may have been deleted in another window.";
        }
        if (cause instanceof DataAccessException) {
            return "The database could not be reached or rejected the request.\n\n"
                 + cause.getMessage()
                 + "\n\nCheck that PostgreSQL and MongoDB are running and that the "
                 + "connection settings in application.properties are correct.";
        }
        String message = cause.getMessage();
        return (message == null || message.isBlank())
                ? cause.getClass().getSimpleName()
                : message;
    }

    /** A failed task can wrap the real exception, so this takes it out. */
    private static Throwable unwrap(Throwable error) {
        if (error == null) {
            return new IllegalStateException("Unknown error");
        }
        if (error instanceof ExecutionException && error.getCause() != null) {
            return error.getCause();
        }
        if (error instanceof RuntimeException
                && error.getCause() != null
                && error.getClass() == RuntimeException.class) {
            return error.getCause();
        }
        return error;
    }
}
