package org.example.bloggingplatformi.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.bloggingplatformi.dao.CommentDao;
import org.example.bloggingplatformi.dao.DaoFactory;
import org.example.bloggingplatformi.dao.PostDao;
import org.example.bloggingplatformi.dao.ReviewDao;
import org.example.bloggingplatformi.dao.TagDao;
import org.example.bloggingplatformi.exception.DataAccessException;
import org.example.bloggingplatformi.exception.EntityNotFoundException;
import org.example.bloggingplatformi.exception.ValidationException;
import org.example.bloggingplatformi.model.Page;
import org.example.bloggingplatformi.model.Post;
import org.example.bloggingplatformi.model.PostFilter;
import org.example.bloggingplatformi.model.PostStatus;
import org.example.bloggingplatformi.model.PostSummary;
import org.example.bloggingplatformi.model.RatingStats;
import org.example.bloggingplatformi.model.Tag;
import org.example.bloggingplatformi.util.Excerpts;
import org.example.bloggingplatformi.util.Slugs;

/**
 * Business rules for posts: validation, slugs, excerpts, tags, and
 * publishing or unpublishing. The excerpt and view count columns are
 * only changed from this class, so they stay correct.
 */
public class PostService {

    private static final int MAX_TITLE = 255;

    private final PostDao postDao;
    private final TagDao tagDao;

    /** These two are null in unit tests that do not need comment and review counts. */
    private final CommentDao commentDao;
    private final ReviewDao reviewDao;

    public PostService() {
        this(DaoFactory.posts(), DaoFactory.tags(), DaoFactory.comments(), DaoFactory.reviews());
    }

    public PostService(PostDao postDao, TagDao tagDao) {
        this(postDao, tagDao, null, null);
    }

    public PostService(PostDao postDao, TagDao tagDao, CommentDao commentDao, ReviewDao reviewDao) {
        this.postDao = postDao;
        this.tagDao = tagDao;
        this.commentDao = commentDao;
        this.reviewDao = reviewDao;
    }

    public Optional<Post> findById(long postId) {
        return postDao.findById(postId);
    }

    public Post require(long postId) {
        return postDao.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }

    public Page<PostSummary> search(PostFilter filter) {
        Page<PostSummary> page = postDao.search(filter == null ? PostFilter.all() : filter);
        List<PostSummary> decorated = withEngagement(page.content());
        return new Page<>(decorated, page.pageNumber(), page.pageSize(), page.totalElements());
    }

    /**
     * Adds comment counts and ratings to each post, read from the database
     * that stores them. It does two lookups per page, not one per post.
     * If that database is down, the posts are still shown with the numbers marked unknown.
     */
    private List<PostSummary> withEngagement(List<PostSummary> summaries) {
        if (summaries.isEmpty() || commentDao == null || reviewDao == null) {
            return summaries;
        }
        List<Long> ids = summaries.stream().map(PostSummary::postId).toList();
        try {
            Map<Long, Long> comments = commentDao.countVisibleByPostIds(ids);
            Map<Long, RatingStats> ratings = reviewDao.statsByPostIds(ids);
            return summaries.stream()
                    .map(s -> s.withEngagement(
                            comments.getOrDefault(s.postId(), 0L),
                            ratings.getOrDefault(s.postId(), RatingStats.NONE)))
                    .toList();
        } catch (DataAccessException e) {
            return summaries.stream().map(PostSummary::withEngagementUnavailable).toList();
        }
    }

    public List<Tag> tagsOf(long postId) {
        return tagDao.findByPostId(postId);
    }

    public long count() {
        return postDao.count();
    }

    public long countPublished() {
        return postDao.countByStatus(PostStatus.PUBLISHED.name());
    }

    /**
     * Creates a post and attaches its tags in one transaction.
     *
     * @param tagNames tag names as typed; new tags are created
     */
    public Post create(long authorId, String title, String body,
                       PostStatus status, List<String> tagNames) {
        validate(title, body, authorId);

        PostStatus effective = (status == null) ? PostStatus.DRAFT : status;

        // The slug is made once here and never changes, so old links keep working
        // even if the title is edited.
        String slug = Slugs.uniquify(
                Slugs.forPost(title), Slugs.MAX_POST_SLUG_LENGTH, postDao::existsBySlug);

        Post post = Post.builder()
                .authorId(authorId)
                .title(title.strip())
                .slug(slug)
                .body(body)
                .excerpt(Excerpts.from(body))
                .status(effective)
                // The database requires a published date only on PUBLISHED posts.
                .publishedAt(effective == PostStatus.PUBLISHED ? OffsetDateTime.now() : null)
                .build();

        long postId = postDao.insertWithTags(post, resolveTagIds(tagNames));
        return post.withId(postId);
    }

    /** Updates title, body, status and tags. The slug does not change. */
    public Post update(long postId, String title, String body,
                       PostStatus status, List<String> tagNames) {
        Post existing = require(postId);
        validate(title, body, existing.authorId());

        PostStatus effective = (status == null) ? existing.status() : status;

        Post updated = existing.toBuilder()
                .title(title.strip())
                .body(body)
                // Rebuild the excerpt so it always matches the body.
                .excerpt(Excerpts.from(body))
                .status(effective)
                .publishedAt(resolvePublishedAt(existing, effective))
                .build();

        if (!postDao.updateWithTags(updated, resolveTagIds(tagNames))) {
            throw new EntityNotFoundException("Post", postId);
        }
        return updated;
    }

    /**
     * A published post keeps its old date, or gets today's date if it has none.
     * Any other status clears the date, as the database check requires.
     */
    private OffsetDateTime resolvePublishedAt(Post existing, PostStatus newStatus) {
        if (newStatus == PostStatus.PUBLISHED) {
            return existing.publishedAt() != null ? existing.publishedAt() : OffsetDateTime.now();
        }
        return null;
    }

    public Post publish(long postId) {
        Post published = require(postId).publish(OffsetDateTime.now());
        if (!postDao.updateWithTags(published, null)) {
            throw new EntityNotFoundException("Post", postId);
        }
        return published;
    }

    public Post unpublish(long postId) {
        Post draft = require(postId).unpublish();
        if (!postDao.updateWithTags(draft, null)) {
            throw new EntityNotFoundException("Post", postId);
        }
        return draft;
    }

    /**
     * Deletes the post with its comments, reviews and tag links.
     * Comments and reviews may be in MongoDB, where the PostgreSQL cascade does
     * not reach, so they are deleted here first.
     */
    public boolean delete(long postId) {
        if (commentDao != null) {
            commentDao.deleteByPostId(postId);
        }
        if (reviewDao != null) {
            reviewDao.deleteByPostId(postId);
        }
        return postDao.deleteById(postId);
    }

    /**
     * Adds one to the view count. The database does the addition itself,
     * so two views at the same time are both counted.
     */
    public void recordView(long postId) {
        postDao.incrementViewCount(postId);
    }

    /** Turns free-text tag names into ids, creating any that are new. */
    private Set<Integer> resolveTagIds(List<String> tagNames) {
        if (tagNames == null) {
            return null;
        }
        // A set drops a tag typed twice, which post_tags would reject.
        // LinkedHashSet also keeps the order the author typed.
        Set<Integer> ids = new LinkedHashSet<>();
        for (String name : tagNames) {
            if (name != null && !name.isBlank()) {
                ids.add(tagDao.findOrCreate(name));
            }
        }
        return ids;
    }

    private void validate(String title, String body, long authorId) {
        ValidationException.Collector errors = new ValidationException.Collector();

        if (title == null || title.isBlank()) {
            errors.reject("title", "A title is required.");
        } else {
            errors.rejectIf(title.strip().length() > MAX_TITLE,
                    "title", "Titles must be at most " + MAX_TITLE + " characters.");
        }
        errors.rejectIf(body == null || body.isBlank(), "body", "A post needs a body.");
        errors.rejectIf(authorId <= 0, "author", "A valid author is required.");

        errors.throwIfInvalid();
    }
}
