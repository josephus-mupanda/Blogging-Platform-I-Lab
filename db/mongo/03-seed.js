// =====================================================================
// Smart Blogging Platform: MongoDB starting data.
//
// Run as:  mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/03-seed.js
//
// Empties the comments and reviews collections. They are filled through
// the app, the same as the PostgreSQL tables (see db/postgres/V3__seed.sql).
// The collections and their indexes stay (01-collections.js, 02-indexes.js).
// =====================================================================

// `db` already points at blogging_platform because of the connection string.
db.comments.deleteMany({});
db.reviews.deleteMany({});

print('comments: ' + db.comments.countDocuments());
print('reviews:  ' + db.reviews.countDocuments());
