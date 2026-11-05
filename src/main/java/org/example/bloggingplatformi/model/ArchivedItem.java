package org.example.bloggingplatformi.model;

import java.time.LocalDateTime;

/** One entry in the admin trash. Only comments have a parent post ID. */
public record ArchivedItem(Kind kind, String id, String title, String content, LocalDateTime archivedAt,
                           Long parentPostId) {

    public enum Kind {
        POST("Posts"), COMMENT("Comments"), TAG("Tags");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
