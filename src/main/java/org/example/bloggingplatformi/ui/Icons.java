package org.example.bloggingplatformi.ui;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Vector icons as inline SVG paths.
 *
 * <p>Drawn rather than loaded from an icon font, for two reasons: a font has
 * to ship with the application and be registered before first use, and a glyph
 * that is missing renders as a box rather than failing loudly. An
 * {@link SVGPath} is resolution-independent and always available.
 *
 * <p>All paths are on a 24x24 grid and are scaled to the requested size, so
 * icons line up optically regardless of where they are used.
 */
public final class Icons {

    private static final double GRID = 24.0;

    public static final String DASHBOARD =
            "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z";

    public static final String ARTICLE =
            "M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm-5 14H7v-2h7v2z"
          + "m3-4H7v-2h10v2zm0-4H7V7h10v2z";

    public static final String COMMENT =
            "M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2z"
          + "m0-3H6V6h12v2z";

    public static final String TAG =
            "M21.41 11.58l-9-9A2 2 0 0 0 11 2H4a2 2 0 0 0-2 2v7a2 2 0 0 0 .59 1.42l9 9a2 2 0 0 0 2.82 0"
          + "l7-7a2 2 0 0 0 0-2.84zM5.5 7A1.5 1.5 0 1 1 7 5.5 1.5 1.5 0 0 1 5.5 7z";

    public static final String SHIELD =
            "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-1 6h2v6h-2V7zm0 8h2v2h-2v-2z";

    public static final String PERSON =
            "M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5zm0 2c-3.33 0-10 1.67-10 5v3h20v-3c0-3.33-6.67-5-10-5z";

    public static final String SEARCH =
            "M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16a6.47 6.47 0 0 0 4.23-1.57"
          + "l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z";

    public static final String PLUS =
            "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";

    public static final String EDIT =
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34"
          + "a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";

    public static final String TRASH =
            "M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";

    public static final String EYE =
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5z"
          + "m0 12.5a5 5 0 1 1 5-5 5 5 0 0 1-5 5zm0-8a3 3 0 1 0 3 3 3 3 0 0 0-3-3z";

    public static final String REFRESH =
            "M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78"
          + "L13 11h7V4l-2.35 2.35z";

    public static final String BACK =
            "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";

    public static final String STAR =
            "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";

    public static final String CHART =
            "M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z";

    public static final String DATABASE =
            "M12 2C7.58 2 4 3.79 4 6s3.58 4 8 4 8-1.79 8-4-3.58-4-8-4zm0 10c-4.42 0-8-1.79-8-4v4"
          + "c0 2.21 3.58 4 8 4s8-1.79 8-4V8c0 2.21-3.58 4-8 4zm0 6c-4.42 0-8-1.79-8-4v4c0 2.21 3.58 4 8 4"
          + "s8-1.79 8-4v-4c0 2.21-3.58 4-8 4z";

    private Icons() {
    }

    /** An icon at {@code size} pixels, styled by the {@code nav-icon} class. */
    public static Group of(String path, double size) {
        return of(path, size, null);
    }

    /**
     * An icon at {@code size} pixels in an explicit colour.
     *
     * <p>Wrapped in a {@link Group} so the scaled bounds are reported
     * correctly; scaling an {@code SVGPath} directly leaves the layout engine
     * reserving space for the original 24x24 geometry.
     */
    public static Group of(String path, double size, Color fill) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.getStyleClass().add("nav-icon");

        double scale = size / GRID;
        svg.setScaleX(scale);
        svg.setScaleY(scale);

        if (fill != null) {
            svg.setFill(fill);
        }
        return new Group(svg);
    }
}
