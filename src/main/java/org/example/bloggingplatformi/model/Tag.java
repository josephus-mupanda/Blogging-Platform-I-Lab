package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

import org.example.bloggingplatformi.util.Slugs;

/**
 * A row of {@code tags}.
 *
 * <p>Names are stored lower-case. That is enforced by the
 * {@code ck_tags_lower} CHECK constraint, so "Java" and "java" cannot both
 * exist; the compact constructor normalizes rather than letting the insert
 * fail downstream.
 */
public record Tag(
        int tagId,
        String name,
        String slug,
        OffsetDateTime createdAt
) {

    public Tag {
        Objects.requireNonNull(name, "name");
        name = name.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("tag name must not be blank");
        }
        if (slug == null || slug.isBlank()) {
            slug = Slugs.forTag(name);
        }
    }

    /** A new, not-yet-persisted tag. The slug is derived from the name. */
    public static Tag of(String name) {
        return new Tag(0, name, null, null);
    }

    public boolean isPersisted() {
        return tagId > 0;
    }

    public Tag withId(int id) {
        return new Tag(id, name, slug, createdAt);
    }
}
