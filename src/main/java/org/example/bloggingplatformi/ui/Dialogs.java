package org.example.bloggingplatformi.ui;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Small pop-up windows: an error message, or a yes / no question. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        style(alert, "Blogfold");
        alert.setHeaderText("Something went wrong");
        alert.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("primary");
        alert.showAndWait();
    }

    public static boolean confirm(String title, String message, String action, boolean destructive) {
        ButtonType confirm = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, confirm);
        style(alert, title);
        alert.setHeaderText(title);
        Button button = (Button) alert.getDialogPane().lookupButton(confirm);
        button.getStyleClass().add(destructive ? "danger" : "primary");
        if (destructive) {
            button.setDefaultButton(false);
            Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
            cancel.setDefaultButton(true);
            alert.setOnShown(event -> cancel.requestFocus());
        }
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }

    public static Optional<String> editComment(String text) {
        Dialog<String> dialog = new Dialog<>();
        style(dialog, "Edit comment");
        dialog.setHeaderText("Edit comment");
        TextArea input = new TextArea(text);
        input.setAccessibleText("Comment text");
        input.setWrapText(true);
        input.setPrefRowCount(4);
        dialog.getDialogPane().setContent(input);
        ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, save);
        dialog.getDialogPane().lookupButton(save).getStyleClass().add("primary");
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(input.textProperty().isEmpty());
        dialog.setResultConverter(button -> button == save ? input.getText() : null);
        dialog.setOnShown(event -> input.requestFocus());
        return dialog.showAndWait();
    }

    private static void style(Dialog<?> dialog, String title) {
        dialog.setTitle(title);
        Window.getWindows().stream().filter(Window::isFocused).findFirst().ifPresent(dialog::initOwner);
        dialog.getDialogPane().setPrefWidth(430);
        dialog.getDialogPane().getStylesheets().add(
                Dialogs.class.getResource("/org/example/bloggingplatformi/css/app.css").toExternalForm());
    }
}
