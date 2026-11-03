// =====================================================================
// Smart Blogging Platform: MongoDB indexes.
//
// Run as:  mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/02-indexes.js
//
// Kept apart from 01-collections.js so queries can be timed without
// indexes first. Use the drop block at the bottom to remove them.
// =====================================================================

// `db` already points at blogging_platform because of the connection string.
// Do not write `const db = db.getSiblingDB(...)`: it throws an error.

// ---------------------------------------------------------------------
// comments
// ---------------------------------------------------------------------

// Speeds up loading one post's comments in date order.
// The filter field comes first and the sort field second.
db.comments.createIndex(
  { postId: 1, createdAt: 1 },
  { name: 'idx_comments_post_created' }
);

// Speeds up reading a reply tree with a prefix match on path.
// postId comes first so only one post's comments are scanned.
db.comments.createIndex(
  { postId: 1, path: 1 },
  { name: 'idx_comments_post_path' }
);

// Speeds up finding the direct replies to a comment.
// Sparse, so documents without a parentId field are left out.
db.comments.createIndex(
  { parentId: 1 },
  { name: 'idx_comments_parent', sparse: true }
);

// Speeds up "comments by user", newest first.
db.comments.createIndex(
  { authorId: 1, createdAt: -1 },
  { name: 'idx_comments_author_created' }
);

// Speeds up keyword search in comment bodies.
// MongoDB allows only one text index per collection. PostgreSQL has no such limit.
db.comments.createIndex(
  { body: 'text' },
  { name: 'idx_comments_body_text', default_language: 'english' }
);

// Speeds up the moderation queue. Only HIDDEN and FLAGGED comments are indexed.
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

// Allows only one review per user per post, the same rule as the SQL
// UNIQUE constraint.
db.reviews.createIndex(
  { postId: 1, reviewerId: 1 },
  { name: 'uq_reviews_post_reviewer', unique: true }
);

// Speeds up rating stats and "highest rated first" for a post.
db.reviews.createIndex(
  { postId: 1, rating: -1 },
  { name: 'idx_reviews_post_rating' }
);

// Speeds up "reviews by user", newest first.
db.reviews.createIndex(
  { reviewerId: 1, createdAt: -1 },
  { name: 'idx_reviews_reviewer_created' }
);

print('comments indexes: ' + db.comments.getIndexes().map(i => i.name).join(', '));
print('reviews indexes:  ' + db.reviews.getIndexes().map(i => i.name).join(', '));

// =====================================================================
// Remove the indexes above, for benchmarking without indexes.
// Uncomment to run. The _id index always stays.
// =====================================================================
// db.comments.dropIndexes();
// db.reviews.dropIndexes();
