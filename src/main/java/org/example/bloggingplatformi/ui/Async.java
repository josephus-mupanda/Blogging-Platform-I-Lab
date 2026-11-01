package org.example.bloggingplatformi.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;

/**
 * Runs database work off the JavaFX application thread.
 *
 * <p>This is not optional politeness. The FX thread draws every frame, so a
 * query that blocks it for 300ms freezes the window for 300ms — and the slow
 * queries are precisely the ones Phase 4 is meant to study, so the UI would be
 * at its worst exactly when it is being demonstrated. Every service call from
 * a view goes through here.
 *
 * <p>The pool is small and daemon-threaded: daemon so a forgotten task cannot
 * keep the JVM alive after the window closes, and small because the Hikari
 * pool is the real limit on concurrency anyway.
 */
public final class Async {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, daemonFactory());

    private Async() {
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "db-worker");
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Runs {@code work} in the background and hands the result to
     * {@code onSuccess} back on the FX thread.
     *
     * <p>Failures go to {@link Dialogs#error}. Swallowing them would leave the
     * UI showing stale data with no indication that anything went wrong, which
     * is worse than an error dialog.
     */
    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, null);
    }

    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (onFailure != null) {
                onFailure.accept(error);
            } else {
                Dialogs.error("Something went wrong", error);
            }
        });

        EXECUTOR.execute(task);
    }

    /**
     * As {@link #run}, but disables {@code busyNode} for the duration.
     *
     * <p>Prevents the double-submit that happens when a save takes long enough
     * for the user to click again.
     */
    public static <T> void runDisabling(Node busyNode, Supplier<T> work, Consumer<T> onSuccess) {
        busyNode.setDisable(true);
        run(work,
            result -> {
                busyNode.setDisable(false);
                onSuccess.accept(result);
            },
            error -> {
                busyNode.setDisable(false);
                Dialogs.error("Something went wrong", error);
            });
    }

    /** Fire-and-forget background work, for things like the view counter. */
    public static void runQuietly(Runnable work) {
        EXECUTOR.execute(() -> {
            try {
                work.run();
            } catch (RuntimeException ignored) {
                // Deliberately silent: a failed view-count increment must not
                // interrupt someone reading a post.
            }
        });
    }

    /** Runs on the FX thread, whether or not the caller is already on it. */
    public static void onFxThread(Runnable work) {
        if (Platform.isFxApplicationThread()) {
            work.run();
        } else {
            Platform.runLater(work);
        }
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
