package org.example.bloggingplatformi.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/** Shared controls for browsing and removing tags. */
public final class TagChips {

    private TagChips() {
    }

    public static Button link(String label, String accessibleText, Runnable open) {
        Button chip = new Button(label);
        chip.getStyleClass().add("tag-link");
        wrapLabel(chip);
        chip.setAccessibleText(accessibleText);
        chip.setOnAction(event -> open.run());
        return chip;
    }

    public static HBox removable(Node content, String actionLabel, Runnable remove) {
        if (content instanceof Labeled label) {
            wrapLabel(label);
        }
        Button close = new Button("×");
        close.getStyleClass().add("tag-remove");
        close.setAccessibleText(actionLabel);
        close.setTooltip(new Tooltip(actionLabel));
        close.setOnAction(event -> remove.run());

        HBox chip = new HBox(4, content, close);
        chip.getStyleClass().add("tag-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        return chip;
    }

    private static void wrapLabel(Labeled label) {
        label.setMinWidth(0);
        label.setMaxWidth(210);
        label.setWrapText(true);
    }
}
