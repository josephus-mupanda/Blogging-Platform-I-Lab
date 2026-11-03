// =====================================================================
// Smart Blogging Platform: MongoDB collections with validation rules.
//
// Run as:  mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/01-collections.js
//
// $jsonSchema checks the fields that must be correct (postId, rating, body).
// Flexible fields (reactions, criteria, editHistory) can hold any keys.
// =====================================================================

// `db` already points at blogging_platform because of the connection string.
// Do not write `const db = db.getSiblingDB(...)`: it throws an error.

// ---------------------------------------------------------------------
// comments
//
// One document per comment, not replies nested inside their parent.
// Nested replies could pass the 16 MB document limit on a busy post.
// `path` lists the ancestor ids ("/661f.../889a.../"), so a whole reply
// tree can be read with one prefix match on an index.
// ---------------------------------------------------------------------
db.createCollection('comments', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['postId', 'authorId', 'authorName', 'path', 'depth',
                 'body', 'status', 'createdAt', 'updatedAt'],
      properties: {
        postId: {
          bsonType: 'long',
          description: 'References PostgreSQL posts.post_id. No cross-store FK exists, so the service layer owns this invariant.'
        },
        authorId:   { bsonType: 'long' },
        authorName: {
          bsonType: 'string',
          description: 'Denormalized. There is no join available across stores, and a comment list must not fan out into N SQL lookups.'
        },
        parentId: {
          bsonType: ['objectId', 'null'],
          description: 'null at the top level.'
        },
        path: {
          bsonType: 'string',
          pattern: '^/([0-9a-f]{24}/)*$',
          description: 'Ancestor ids, root first. "/" at the top level.'
        },
        depth:  { bsonType: 'int', minimum: 0, maximum: 32 },
        body:   { bsonType: 'string', minLength: 1, maxLength: 10000 },
        status: { enum: ['VISIBLE', 'HIDDEN', 'FLAGGED'] },
        reactions: {
          bsonType: 'object',
          description: 'Open by design: reaction kinds are added without a migration.',
          additionalProperties: { bsonType: 'int', minimum: 0 }
        },
        editHistory: {
          bsonType: 'array',
          description: 'Unbounded per-document history. In SQL this is a second table; here it is a field.',
          items: {
            bsonType: 'object',
            required: ['body', 'editedAt'],
            properties: {
              body:     { bsonType: 'string' },
              editedAt: { bsonType: 'date' }
            }
          }
        },
        createdAt: { bsonType: 'date' },
        updatedAt: { bsonType: 'date' }
      }
    }
  },
  validationLevel: 'strict',
  validationAction: 'error'
});

// ---------------------------------------------------------------------
// reviews
//
// `criteria` holds optional sub-scores, and the keys can differ per review.
// In SQL this would need many mostly empty columns or an extra table.
// ---------------------------------------------------------------------
db.createCollection('reviews', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['postId', 'reviewerId', 'reviewerName', 'rating',
                 'createdAt', 'updatedAt'],
      properties: {
        postId:       { bsonType: 'long' },
        reviewerId:   { bsonType: 'long' },
        reviewerName: { bsonType: 'string' },
        rating: {
          bsonType: 'int',
          minimum: 1,
          maximum: 5,
          description: 'Mirrors the SQL CHECK (rating BETWEEN 1 AND 5).'
        },
        headline: { bsonType: ['string', 'null'], maxLength: 150 },
        body:     { bsonType: ['string', 'null'], maxLength: 10000 },
        criteria: {
          bsonType: 'object',
          description: 'Optional sub-scores; the set of keys varies per review.',
          additionalProperties: { bsonType: 'int', minimum: 1, maximum: 5 }
        },
        helpfulVotes: { bsonType: 'int', minimum: 0 },
        createdAt:    { bsonType: 'date' },
        updatedAt:    { bsonType: 'date' }
      }
    }
  },
  validationLevel: 'strict',
  validationAction: 'error'
});

print('Collections created: ' + db.getCollectionNames().join(', '));
