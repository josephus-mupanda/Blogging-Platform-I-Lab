// =====================================================================
// Smart Blogging Platform - MongoDB collection design
// Phase 1 / Epic 4 - User Story 4.2
//
// Run as:  mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/01-collections.js
//
// "Schemaless" does not mean "unvalidated". $jsonSchema gives rigidity
// where the data needs it (postId, rating, body) while leaving the
// genuinely open-ended fields (reactions, criteria, editHistory) free.
// That combination is the actual argument for using a document store
// here rather than a sparse SQL table.
// =====================================================================

// The target database comes from the connection string in the mongosh
// invocation, so `db` is already blogging_platform. Reassigning it with
// `const db = db.getSiblingDB(...)` would throw: the new binding shadows
// the global before the right-hand side can read it.

// ---------------------------------------------------------------------
// comments
//
// Flat documents with a materialized path, NOT nested subdocuments.
// Nesting replies inside their parent risks the 16 MB document ceiling on
// a popular post and turns "edit one reply" into a whole-document rewrite.
// The path ("/661f.../889a.../") makes a subtree read one indexed prefix
// match while each comment stays independently updatable.
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
// `criteria` is the clearest case for documents in this domain: an
// optional, per-review-variable set of sub-scores. The SQL equivalent is
// either a wide sparse table or an EAV side table.
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
