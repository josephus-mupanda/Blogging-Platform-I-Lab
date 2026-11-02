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
 * Business rules for posts: validation, slug and excerpt generation, tag
 * resolution and the publish/unpublish transitions.
 *
 * <p>The two denormalized columns are maintained here, which is what keeps
 * them from drifting: {@code excerpt} is regenerated on every body change, and
 * {@code view_count} is only ever moved by {@link #recordView}.
 */
public class PostService {

    private static final int MAX_TITLE = 255;

    private final PostDao postDao;
    private final TagDao tagDao;

    /** Null when engagement figures are not wanted, as in unit tests. */
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

    public Optional<Post> findBySlug(String slug) {
        return postDao.findBySlug(slug);
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

    public List<PostSummary> findByAuthor(long authorId, int limit, int offset) {
        return withEngagement(postDao.findByAuthor(authorId, limit, offset));
    }

    /**
     * Fills in comment and review figures from whichever store holds them.
     *
     * <p>These used to come from subqueries in the post query, which always
     * read PostgreSQL. With {@code app.comments.store=MONGO} the list then
     * showed the relational table's counts while the detail screen showed
     * MongoDB's - "8 comments" in the list, 141 on opening the post. Two
     * batched lookups per page (not one per row) read the active store instead.
     *
     * <p>If that store is down the posts are still listed, with the figures
     * marked unknown: the post list must not fail because MongoDB did.
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
     * @param tagNames free text; each is normalized and created if new
     */
    public Post create(long authorId, String title, String body,
                       PostStatus status, List<String> tagNames) {
        validate(title, body, authorId);

        PostStatus effective = (status == null) ? PostStatus.DRAFT : status;

        // Generated once, at creation, and never regenerated: the slug is an
        // immutable external identifier, so editing the title later must not
        // break links that already point at the post.
        String slug = Slugs.uniquify(
                Slugs.forPost(title), Slugs.MAX_POST_SLUG_LENGTH, postDao::existsBySlug);

        Post post = Post.builder()
                .authorId(authorId)
                .title(title.strip())
                .slug(slug)
                .body(body)
                .excerpt(Excerpts.from(body))
                .status(effective)
                // The ck_posts_published_at constraint requires a date on a
                // PUBLISHED row and rejects one on any other status.
                .publishedAt(effective == PostStatus.PUBLISHED ? OffsetDateTime.now() : null)
                .build();

        long postId = postDao.insertWithTags(post, resolveTagIds(tagNames));
        return post.withId(postId);
    }

    /** Updates title, body, status and tags. The slug is left alone by design. */
    public Post update(long postId, String title, String body,
                       PostStatus status, List<String> tagNames) {
        Post existing = require(postId);
        validate(title, body, existing.authorId());

        PostStatus effective = (status == null) ? existing.status() : status;

        Post updated = existing.toBuilder()
                .title(title.strip())
                .body(body)
                // Regenerated on every body change, which is the mechanism
                // that keeps this denormalization honest.
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
     * Keeps {@code status} and {@code published_at} consistent with the CHECK
     * constraint: publishing stamps a date if there is not one already,
     * and leaving PUBLISHED clears it.
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
     * Deletes the post. Comments, reviews and tag links in PostgreSQL go with
     * it through ON DELETE CASCADE.
     *
     * <p>When comments live in MongoDB there is no cross-store cascade, so
     * {@link CommentService#deleteForPost} has to be called as well. That
     * asymmetry is a genuine cost of polyglot persistence and is recorded in
     * the Phase 4 comparison rather than hidden here.
     */
    public boolean delete(long postId) {
        return postDao.deleteById(postId);
    }

    /**
     * Records a read.
     *
     * <p>Delegates to {@code SET view_count = view_count + 1}: the
     * read-modify-write happens inside the database, so two concurrent views
     * cannot both read the same number and lose an increment.
     */
    public void recordView(long postId) {
        postDao.incrementViewCount(postId);
    }

    /** Turns free-text tag names into ids, creating any that are new. */
    private Set<Integer> resolveTagIds(List<String> tagNames) {
        if (tagNames == null) {
            return null;
        }
        // LinkedHashSet so the same tag typed twice collapses (the composite
        // PK on post_tags would otherwise reject the second insert) while the
        // author's ordering survives.
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
