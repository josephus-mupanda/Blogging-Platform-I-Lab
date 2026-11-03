package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

import org.example.bloggingplatformi.util.Slugs;

/**
 * A row of the tags table. The database only allows lower-case names,
 * so the constructor lower-cases the name before it is saved.
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

    /** A new tag, not yet saved. The slug is made from the name. */
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
