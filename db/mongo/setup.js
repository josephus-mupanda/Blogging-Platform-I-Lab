// =====================================================================
// Smart Blogging Platform: MongoDB collections for comments and reviews.
// Run: mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/setup.js
//
// It creates the two collections with simple validation rules, and their
// indexes. Running it again is safe: existing documents are kept.
// =====================================================================

// A comment on a post. postId and authorId are ids from PostgreSQL.
const commentSchema = {
  bsonType: 'object',
  required: ['postId', 'authorId', 'authorName', 'body', 'createdAt'],
  properties: {
    postId:     { bsonType: 'long' },
    authorId:   { bsonType: 'long' },
    authorName: { bsonType: 'string' },
    body:       { bsonType: 'string', minLength: 1 },
    createdAt:  { bsonType: 'date' },
    deletedAt:  { bsonType: 'date' }
  }
};

// A review of a post: a rating from 1 to 5 and an optional text.
const reviewSchema = {
  bsonType: 'object',
  required: ['postId', 'reviewerId', 'reviewerName', 'rating', 'createdAt'],
  properties: {
    postId:       { bsonType: 'long' },
    reviewerId:   { bsonType: 'long' },
    reviewerName: { bsonType: 'string' },
    rating:       { bsonType: 'int', minimum: 1, maximum: 5 },
    text:         { bsonType: 'string' },
    createdAt:    { bsonType: 'date' }
  }
};

// Creates the collection, or updates its rules if it already exists.
function createOrUpdate(name, schema) {
  if (db.getCollectionNames().includes(name)) {
    db.runCommand({ collMod: name, validator: { $jsonSchema: schema } });
  } else {
    db.createCollection(name, { validator: { $jsonSchema: schema } });
  }
}

createOrUpdate('comments', commentSchema);
createOrUpdate('reviews', reviewSchema);

// The comments of a post, oldest first.
db.comments.createIndex({ postId: 1, createdAt: 1 });

// The reviews of a post. "unique" means a user can review a post only once.
db.reviews.createIndex({ postId: 1, reviewerId: 1 }, { unique: true });

print('comments: ' + db.comments.countDocuments() + ', reviews: ' + db.reviews.countDocuments());
