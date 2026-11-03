package org.example.bloggingplatformi.ui;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.concurrent.Task;

/**
 * Runs database calls on a background thread so the window does not freeze,
 * then hands the result back to the JavaFX thread.
 * Every service call from a view goes through this class.
 */
public final class Async {

    /**
     * Number of tasks whose callback has not finished yet. AtomicInteger is a
     * counter that is safe to change from several threads.
     * The UI test waits for this to reach zero instead of using sleeps.
     */
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();

    private Async() {
    }

    /**
     * Runs {@code work} in the background and passes the result to {@code onSuccess} on the FX thread.
     * If it fails, an error dialog is shown.
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

        task.setOnSucceeded(event -> {
            try {
                onSuccess.accept(task.getValue());
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        });
        task.setOnFailed(event -> {
            try {
                Throwable error = task.getException();
                if (onFailure != null) {
                    onFailure.accept(error);
                } else {
                    Dialogs.error("Something went wrong", error);
                }
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        });

        IN_FLIGHT.incrementAndGet();
        startInBackground(task);
    }

    // A daemon thread does not keep the app running after the window is closed.
    private static void startInBackground(Runnable work) {
        Thread thread = new Thread(work);
        thread.setDaemon(true);
        thread.start();
    }

    /** Background tasks whose FX-thread callback has not finished yet. */
    public static int inFlight() {
        return IN_FLIGHT.get();
    }

    /** Runs background work with no callback, for things like the view counter. */
    public static void runQuietly(Runnable work) {
        IN_FLIGHT.incrementAndGet();
        startInBackground(() -> {
            try {
                work.run();
            } catch (RuntimeException ignored) {
                // A failed view count should not interrupt someone reading a post.
            } finally {
                IN_FLIGHT.decrementAndGet();
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
}
