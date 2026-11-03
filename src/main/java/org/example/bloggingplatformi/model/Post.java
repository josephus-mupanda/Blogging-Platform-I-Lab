package org.example.bloggingplatformi.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A row of the posts table. It has eleven fields, so a Builder is used to create it.
 * A PUBLISHED post must have a published date and other posts must not.
 * Use publish() and unpublish() to change between them.
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

    /** Sets the post to PUBLISHED and gives it a published date. */
    public Post publish(OffsetDateTime when) {
        return toBuilder()
                .status(PostStatus.PUBLISHED)
                .publishedAt(when == null ? OffsetDateTime.now() : when)
                .build();
    }

    /**
     * Sets the post back to DRAFT and clears the published date,
     * because the database does not allow a date on an unpublished post.
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

    /** Collects the fields one at a time, then builds the Post. */
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
