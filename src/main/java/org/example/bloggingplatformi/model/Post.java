package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A row of {@code posts}.
 *
 * <p>Eleven components is past the point where a positional constructor is
 * readable at the call site, so {@link Builder} exists for construction while
 * the record itself stays immutable.
 *
 * <p>The {@code status}/{@code publishedAt} pairing mirrors the
 * {@code ck_posts_published_at} constraint: PUBLISHED requires a date, and the
 * other two states must not carry one. {@link #publish} and {@link #unpublish}
 * are the supported ways to move between them.
 */
public record Post(
        long postId,
        long authorId,
        String title,
        String slug,
        String body,
        String excerpt,
        PostStatus status,
        int viewCount,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public Post {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(status, "status");
        if (viewCount < 0) {
            throw new IllegalArgumentException("viewCount must not be negative");
        }
    }

    public boolean isPersisted() {
        return postId > 0;
    }

    public Post withId(long id) {
        return toBuilder().postId(id).build();
    }

    /** Moves the post to PUBLISHED, stamping the publication date the constraint requires. */
    public Post publish(OffsetDateTime when) {
        return toBuilder()
                .status(PostStatus.PUBLISHED)
                .publishedAt(when == null ? OffsetDateTime.now() : when)
                .build();
    }

    /**
     * Returns the post to DRAFT and clears {@code publishedAt}, because the
     * database rejects a publication date on an unpublished row.
     */
    public Post unpublish() {
        return toBuilder()
                .status(PostStatus.DRAFT)
                .publishedAt(null)
                .build();
    }

    public Builder toBuilder() {
        return new Builder()
                .postId(postId)
                .authorId(authorId)
                .title(title)
                .slug(slug)
                .body(body)
                .excerpt(excerpt)
                .status(status)
                .viewCount(viewCount)
                .publishedAt(publishedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder for an immutable record. */
    public static final class Builder {
        private long postId;
        private long authorId;
        private String title;
        private String slug;
        private String body;
        private String excerpt;
        private PostStatus status = PostStatus.DRAFT;
        private int viewCount;
        private OffsetDateTime publishedAt;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public Builder postId(long v) { this.postId = v; return this; }
        public Builder authorId(long v) { this.authorId = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder slug(String v) { this.slug = v; return this; }
        public Builder body(String v) { this.body = v; return this; }
        public Builder excerpt(String v) { this.excerpt = v; return this; }
        public Builder status(PostStatus v) { this.status = v; return this; }
        public Builder viewCount(int v) { this.viewCount = v; return this; }
        public Builder publishedAt(OffsetDateTime v) { this.publishedAt = v; return this; }
        public Builder createdAt(OffsetDateTime v) { this.createdAt = v; return this; }
        public Builder updatedAt(OffsetDateTime v) { this.updatedAt = v; return this; }

        public Post build() {
            return new Post(postId, authorId, title, slug, body, excerpt, status,
                    viewCount, publishedAt, createdAt, updatedAt);
        }
    }
}
