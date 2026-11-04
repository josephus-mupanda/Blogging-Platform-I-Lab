package org.example.bloggingplatformi.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Shows a date the same way on every screen, for example "4 Nov 2026, 14:30". */
public final class Dates {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private Dates() {
    }

    public static String format(LocalDateTime date) {
        return date == null ? "" : date.format(FORMAT);
    }
}
