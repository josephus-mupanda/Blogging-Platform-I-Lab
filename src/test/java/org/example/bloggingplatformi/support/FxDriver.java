package org.example.bloggingplatformi.support;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Screen;
import javafx.stage.Window;

import org.example.bloggingplatformi.ui.Async;

/**
 * Helper for UI tests: finds controls by their text, types, clicks, and waits for results.
 * {@link #settle()} waits until no background task is running ({@link Async#inFlight()} is 0)
 * for two checks in a row, so the test does not rely on fixed sleeps.
 */
public final class FxDriver {

    private static final long TIMEOUT_MS = 30_000;

    private FxDriver() {
    }

    public static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        Platform.setImplicitExit(false);
        if (!started.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("JavaFX toolkit did not start");
        }
    }

    // -----------------------------------------------------------------
    // Running on the FX thread
    // -----------------------------------------------------------------

    /** Runs the code on the FX thread and waits for its result. */
    public static <T> T fx(Callable<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError("FX action failed: " + e.getCause(), e.getCause());
        } catch (Exception e) {
            throw new AssertionError("FX action timed out or was interrupted"
                    + openDialogDescription(), e);
        }
    }

    public static void fxRun(Runnable work) {
        fx(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Clicks without waiting. Use it for a click that opens a confirm dialog:
     * showAndWait blocks until the dialog closes, so waiting here would hang the test.
     */
    public static void clickLater(Button button) {
        Platform.runLater(button::fire);
    }

    public static void click(Button button) {
        fxRun(() -> {
            if (button.isDisabled()) {
                throw new AssertionError("Button is disabled: " + describe(button));
            }
            button.fire();
        });
    }

    public static void type(TextInputControl field, String text) {
        fxRun(() -> field.setText(text));
    }

    /** Types the text, then presses Enter. */
    public static void typeAndEnter(TextInputControl field, String text) {
        fxRun(() -> {
            field.setText(text);
            field.fireEvent(new ActionEvent());
        });
    }

    // -----------------------------------------------------------------
    // Waiting
    // -----------------------------------------------------------------

    /** Waits until all background work and its UI updates have finished. */
    public static void settle() {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        int quietPulses = 0;
        while (System.currentTimeMillis() < deadline) {
            // Running an empty task on the FX thread lets earlier queued callbacks finish first.
            fxRun(() -> { });
            if (Async.inFlight() == 0) {
                if (++quietPulses >= 2) {
                    return;
                }
            } else {
                quietPulses = 0;
                if (findDialog().isPresent()) {
                    // A dialog is open and keeps its task running, so stop waiting.
                    return;
                }
            }
            pause(40);
        }
        throw new AssertionError("UI did not settle within " + TIMEOUT_MS + " ms; "
                + Async.inFlight() + " task(s) still running" + openDialogDescription());
    }

    /** Sleeps for a fixed time. Used for app timers, such as the search delay. */
    public static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void waitFor(String description, Callable<Boolean> condition) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(fx(condition))) {
                return;
            }
            pause(50);
        }
        throw new AssertionError("Timed out waiting for: " + description + openDialogDescription());
    }

    // -----------------------------------------------------------------
    // Finding nodes
    // -----------------------------------------------------------------

    /** Returns every node under root, including the content of scroll panes. */
    public static List<Node> descendants(Node root) {
        List<Node> out = new ArrayList<>();
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, out, seen);
        return out;
    }

    private static void collect(Node node, List<Node> out, Set<Node> seen) {
        if (node == null || !seen.add(node)) {
            return;
        }
        out.add(node);
        if (node instanceof ScrollPane scroll) {
            collect(scroll.getContent(), out, seen);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, out, seen);
            }
        }
    }

    /** True if the node and all its parents are visible. */
    public static boolean showing(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    public static <T extends Node> List<T> all(Node root, Class<T> type, Predicate<T> filter) {
        return fx(() -> descendants(root).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(FxDriver::showing)
                .filter(filter)
                .toList());
    }

    public static <T extends Node> T one(Node root, Class<T> type, Predicate<T> filter, String what) {
        List<T> found = all(root, type, filter);
        if (found.isEmpty()) {
            throw new AssertionError("Not found on screen: " + what);
        }
        return found.get(0);
    }

    public static Button button(Node root, String text) {
        return one(root, Button.class, b -> text.equals(b.getText()), "button '" + text + "'");
    }

    /** Finds an icon-only button by its tooltip text. */
    public static Button buttonWithTooltip(Node root, String tooltip) {
        return one(root, Button.class,
                b -> b.getTooltip() != null && tooltip.equals(b.getTooltip().getText()),
                "button with tooltip '" + tooltip + "'");
    }

    public static <T extends TextInputControl> T field(Node root, Class<T> type, String prompt) {
        return one(root, type,
                f -> f.getPromptText() != null && f.getPromptText().startsWith(prompt),
                "field '" + prompt + "'");
    }

    public static boolean hasText(Node root, String text) {
        return !all(root, Labeled.class, l -> text.equals(l.getText())).isEmpty();
    }

    // -----------------------------------------------------------------
    // Dialogs
    // -----------------------------------------------------------------

    public static Optional<DialogPane> findDialog() {
        return fx(() -> Window.getWindows().stream()
                .filter(Window::isShowing)
                .map(Window::getScene)
                .filter(scene -> scene != null && scene.getRoot() instanceof DialogPane)
                .map(scene -> (DialogPane) scene.getRoot())
                .findFirst());
    }

    public static DialogPane awaitDialog() {
        waitFor("a dialog to open", () -> Window.getWindows().stream()
                .anyMatch(w -> w.isShowing() && w.getScene() != null
                        && w.getScene().getRoot() instanceof DialogPane));
        return findDialog().orElseThrow();
    }

    /** Presses the dialog button labelled {@code text}. */
    public static void answerDialog(DialogPane pane, String text) {
        fxRun(() -> {
            ButtonType type = pane.getButtonTypes().stream()
                    .filter(t -> text.equals(t.getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Dialog has no button '" + text
                            + "', only " + pane.getButtonTypes()));
            ((Button) pane.lookupButton(type)).fire();
        });
        waitFor("the dialog to close", () -> pane.getScene() == null
                || pane.getScene().getWindow() == null
                || !pane.getScene().getWindow().isShowing());
    }

    private static String openDialogDescription() {
        try {
            return Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .map(Window::getScene)
                    .filter(scene -> scene != null && scene.getRoot() instanceof DialogPane)
                    .map(scene -> (DialogPane) scene.getRoot())
                    .findFirst()
                    .map(pane -> "; open dialog: '" + pane.getHeaderText() + "' - "
                            + (pane.getContent() instanceof Labeled l ? l.getText() : pane.getContentText()))
                    .orElse("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String describe(Node node) {
        return node instanceof Labeled l ? node.getClass().getSimpleName() + "('" + l.getText() + "')"
                                          : node.getClass().getSimpleName();
    }

    // -----------------------------------------------------------------
    // Screenshots
    // -----------------------------------------------------------------

    /**
     * Saves a screenshot of the scene as a PNG. It uses the screen's scale
     * (for example 150%), so the image is as sharp as the real window.
     */
    public static void screenshot(Scene scene, Path target) {
        WritableImage image = fx(() -> {
            double scale = Screen.getPrimary().getOutputScaleX();
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(Transform.scale(scale, scale));
            return scene.getRoot().snapshot(params, null);
        });
        try {
            Png.write(image, target);
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not write " + target, e);
        }
    }
}
