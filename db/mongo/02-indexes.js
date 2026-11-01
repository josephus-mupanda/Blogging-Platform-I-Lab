// =====================================================================
// Smart Blogging Platform - MongoDB index strategy
// Phase 1 / Epic 1 - User Story 1.2
//
// Run as:  mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/02-indexes.js
//
// Kept separate from 01-collections.js for the same reason the SQL indexes
// are separate from the SQL schema: Phase 4 benchmarks the unindexed
// baseline first. Use the drop block at the bottom to reset.
// =====================================================================

// The target database comes from the connection string in the mongosh
// invocation, so `db` is already blogging_platform. Reassigning it with
// `const db = db.getSiblingDB(...)` would throw: the new binding shadows
// the global before the right-hand side can read it.

// ---------------------------------------------------------------------
// comments
// ---------------------------------------------------------------------

// The dominant read: one post's thread, in order. Equality field first,
// sort field second, the same principle as the SQL composite index.
db.comments.createIndex(
  { postId: 1, createdAt: 1 },
  { name: 'idx_comments_post_created' }
);

// Subtree reads. The materialized path is only useful if a prefix match
// is indexed; scoping by postId first keeps the scanned range small.
db.comments.createIndex(
  { postId: 1, path: 1 },
  { name: 'idx_comments_post_path' }
);

// Direct children of a comment. Sparse: top-level comments have a null
// parentId and do not belong in this index.
db.comments.createIndex(
  { parentId: 1 },
  { name: 'idx_comments_parent', sparse: true }
);

// "My comments", newest first.
db.comments.createIndex(
  { authorId: 1, createdAt: -1 },
  { name: 'idx_comments_author_created' }
);

// Keyword search inside comment bodies. MongoDB permits only one text
// index per collection, which is itself a point for the SQL-vs-NoSQL
// comparison: PostgreSQL has no such restriction.
db.comments.createIndex(
  { body: 'text' },
  { name: 'idx_comments_body_text', default_language: 'english' }
);

// Moderation queue. Partial, so the VISIBLE majority stays out of it.
db.comments.createIndex(
  { status: 1, createdAt: -1 },
  {
    name: 'idx_comments_moderation',
    partialFilterExpression: { status: { $in: ['HIDDEN', 'FLAGGED'] } }
  }
);

// ---------------------------------------------------------------------
// reviews
// ---------------------------------------------------------------------

// Enforces one review per user per post, matching the SQL UNIQUE
// constraint. Without this the document store would silently permit what
// the relational store rejects, and the comparison would be unfair.
db.reviews.createIndex(
  { postId: 1, reviewerId: 1 },
  { name: 'uq_reviews_post_reviewer', unique: true }
);

// Rating distribution and "highest rated first" per post.
db.reviews.createIndex(
  { postId: 1, rating: -1 },
  { name: 'idx_reviews_post_rating' }
);

db.reviews.createIndex(
  { reviewerId: 1, createdAt: -1 },
  { name: 'idx_reviews_reviewer_created' }
);

print('comments indexes: ' + db.comments.getIndexes().map(i => i.name).join(', '));
print('reviews indexes:  ' + db.reviews.getIndexes().map(i => i.name).join(', '));

// =====================================================================
// Baseline reset for Phase 4 benchmarking.
// Uncomment to return both collections to the unindexed state (_id stays).
// =====================================================================
// db.comments.dropIndexes();
// db.reviews.dropIndexes();
