// =====================================================================
// Smart Blogging Platform - MongoDB seed data
// Phase 1
//
// Run as:  mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/03-seed.js
//
// Mirrors the PostgreSQL seed so the Phase 4 SQL-vs-NoSQL comparison runs
// the same workload over the same shape of data. postId values match the
// post_id values produced by db/postgres/V3__seed.sql, so run that first.
//
// Volume: ~5 000 comments (4 000 roots + 1 000 replies) and ~1 500
// reviews, matching the relational side.
// =====================================================================

// The target database comes from the connection string in the mongosh
// invocation, so `db` is already blogging_platform. Reassigning it with
// `const db = db.getSiblingDB(...)` would throw: the new binding shadows
// the global before the right-hand side can read it.

db.comments.deleteMany({});
db.reviews.deleteMany({});

// Deterministic pseudo-random, so benchmark runs are comparable across
// machines. Math.random() cannot be seeded in JS, so we use a small LCG.
// MINSTD, not the textbook glibc constants: 2^31 * 1103515245 exceeds
// Number.MAX_SAFE_INTEGER, so that multiplier silently loses precision and
// the sequence stops being reproducible. 16807 stays well inside the safe range.
let _seed = 42;
function rand() {
  _seed = (_seed * 16807) % 2147483647;
  return _seed / 2147483647;
}
function randInt(min, max) {
  return min + Math.floor(rand() * (max - min + 1));
}
function pick(arr) {
  return arr[randInt(0, arr.length - 1)];
}
function daysAgo(n) {
  return new Date(Date.now() - n * 86400000);
}

const DEMO_USERS = [
  { id: 2, name: 'ada_l' },
  { id: 3, name: 'grace_h' },
  { id: 4, name: 'linus_t' },
  { id: 5, name: 'barbara_l' },
  { id: 6, name: 'reader01' },
  { id: 7, name: 'reader02' }
];

// The relational seed also creates 200 bulk users (user_id 8..207). Mongo
// needs the same author cardinality, otherwise every generated comment maps
// to one of six people and the authorId index looks artificially selective
// next to its PostgreSQL counterpart.
const USER_POOL = DEMO_USERS.slice();
for (let u = 8; u <= 207; u++) {
  USER_POOL.push({ id: u, name: 'user_' + (u - 7) });
}

// Matches the eight hand-written posts in V3__seed.sql.
const DEMO_POST_IDS = [1, 2, 3, 4, 5, 6, 7, 8];
// Matches the ~500 generated posts (post_id 9..508).
const BULK_POST_MIN = 9;
const BULK_POST_MAX = 508;

// ---------------------------------------------------------------------
// 1. Hand-written thread, readable during demos. Shows a three-level
//    reply chain so the materialized path is visible in the data.
// ---------------------------------------------------------------------

const root1Id = new ObjectId();
const reply1Id = new ObjectId();

const demoComments = [
  {
    _id: root1Id,
    postId: NumberLong("1"),
    authorId: NumberLong("6"),
    authorName: 'reader01',
    parentId: null,
    path: '/',
    depth: NumberInt(0),
    body: 'The view_count exception makes sense, but how do you stop it drifting when two requests increment concurrently?',
    status: 'VISIBLE',
    reactions: { like: NumberInt(14), insightful: NumberInt(3) },
    createdAt: daysAgo(29),
    updatedAt: daysAgo(29)
  },
  {
    _id: reply1Id,
    postId: NumberLong("1"),
    authorId: NumberLong("2"),
    authorName: 'ada_l',
    parentId: root1Id,
    path: '/' + root1Id.toHexString() + '/',
    depth: NumberInt(1),
    body: 'Single service method doing UPDATE ... SET view_count = view_count + 1. The read-modify-write never leaves the database, so there is nothing to race.',
    status: 'VISIBLE',
    reactions: { like: NumberInt(22), insightful: NumberInt(9) },
    editHistory: [
      { body: 'Single UPDATE. Nothing to race.', editedAt: daysAgo(28) }
    ],
    createdAt: daysAgo(29),
    updatedAt: daysAgo(28)
  },
  {
    postId: NumberLong("1"),
    authorId: NumberLong("7"),
    authorName: 'reader02',
    parentId: reply1Id,
    path: '/' + root1Id.toHexString() + '/' + reply1Id.toHexString() + '/',
    depth: NumberInt(2),
    body: 'That is the part I always get wrong in application code. Good catch.',
    status: 'VISIBLE',
    reactions: { like: NumberInt(5) },
    createdAt: daysAgo(27),
    updatedAt: daysAgo(27)
  },
  {
    postId: NumberLong("2"),
    authorId: NumberLong("6"),
    authorName: 'reader01',
    parentId: null,
    path: '/',
    depth: NumberInt(0),
    body: 'Does the trigram index get large relative to the table?',
    status: 'VISIBLE',
    reactions: {},
    createdAt: daysAgo(22),
    updatedAt: daysAgo(22)
  },
  {
    postId: NumberLong("4"),
    authorId: NumberLong("7"),
    authorName: 'reader02',
    parentId: null,
    path: '/',
    depth: NumberInt(0),
    body: 'Materialized path plus a prefix index is such an underrated pattern.',
    status: 'VISIBLE',
    reactions: { like: NumberInt(31), insightful: NumberInt(12) },
    createdAt: daysAgo(11),
    updatedAt: daysAgo(11)
  }
];

db.comments.insertMany(demoComments);

// ---------------------------------------------------------------------
// 2. Bulk comments for benchmarking
// ---------------------------------------------------------------------

const BODIES = [
  'Useful breakdown, thanks.',
  'Does this hold under concurrent writes?',
  'I measured something similar on a larger dataset.',
  'The trade-off section is the strongest part.',
  'Would like to see the query plan for this.',
  'This matches what the documentation implies but never states.',
  'Index selectivity is doing all the work here.',
  'Clear writing on a topic that is usually muddled.'
];

const roots = [];
for (let i = 0; i < 4000; i++) {
  const id = new ObjectId();
  const user = pick(USER_POOL);
  const postId = rand() < 0.2
    ? pick(DEMO_POST_IDS)
    : randInt(BULK_POST_MIN, BULK_POST_MAX);
  const created = daysAgo(randInt(1, 700));

  const doc = {
    _id: id,
    postId: NumberLong(String(postId)),
    authorId: NumberLong(String(user.id)),
    authorName: user.name,
    parentId: null,
    path: '/',
    depth: NumberInt(0),
    body: 'Generated comment ' + i + '. ' + pick(BODIES),
    status: i % 50 === 0 ? 'FLAGGED' : (i % 97 === 0 ? 'HIDDEN' : 'VISIBLE'),
    reactions: { like: NumberInt(randInt(0, 40)) },
    createdAt: created,
    updatedAt: created
  };
  roots.push(doc);
}
db.comments.insertMany(roots, { ordered: false });

const REPLY_BODIES = [
  'Agreed.',
  'That matches what I saw.',
  'Only if the index is actually selective, though.',
  'Source for that number?',
  'This is the detail most posts leave out.'
];

const replies = [];
for (let i = 0; i < 1000; i++) {
  const parent = roots[randInt(0, roots.length - 1)];
  const user = pick(USER_POOL);
  const created = new Date(parent.createdAt.getTime() + randInt(1, 10) * 86400000);

  replies.push({
    postId: parent.postId,
    authorId: NumberLong(String(user.id)),
    authorName: user.name,
    parentId: parent._id,
    // Path is the parent's path plus the parent's own id.
    path: parent.path + parent._id.toHexString() + '/',
    // Every parent picked here is a root, so this is always depth 1.
    // Avoids arithmetic on the NumberInt wrapper, which is not plain addition.
    depth: NumberInt(1),
    body: 'Generated reply ' + i + '. ' + pick(REPLY_BODIES),
    status: 'VISIBLE',
    reactions: { like: NumberInt(randInt(0, 15)) },
    createdAt: created,
    updatedAt: created
  });
}
db.comments.insertMany(replies, { ordered: false });

// ---------------------------------------------------------------------
// 3. Reviews
//
// The unique index on (postId, reviewerId) will reject duplicates, so the
// pairs are deduplicated here first rather than relying on error swallowing.
// ---------------------------------------------------------------------

const CRITERIA_KEYS = ['clarity', 'depth', 'accuracy', 'practicality', 'originality'];
const seen = new Set();
const reviews = [];

for (let i = 0; i < 2000 && reviews.length < 1500; i++) {
  const user = pick(USER_POOL);
  const postId = rand() < 0.2
    ? pick(DEMO_POST_IDS)
    : randInt(BULK_POST_MIN, BULK_POST_MAX);
  const key = postId + ':' + user.id;
  if (seen.has(key)) continue;
  seen.add(key);

  const rating = randInt(1, 5);
  const created = daysAgo(randInt(1, 700));

  const doc = {
    postId: NumberLong(String(postId)),
    reviewerId: NumberLong(String(user.id)),
    reviewerName: user.name,
    rating: NumberInt(rating),
    headline: 'Generated review: ' + ['weak', 'mixed', 'fair', 'good', 'excellent'][rating - 1],
    body: rating >= 4 ? 'Clear and well evidenced.' : null,
    helpfulVotes: NumberInt(randInt(0, 50)),
    createdAt: created,
    updatedAt: created
  };

  // Only about half the reviews carry sub-scores, and the set of keys
  // varies. That variability is the whole point of the document model.
  if (rand() < 0.5) {
    const criteria = {};
    const count = randInt(2, CRITERIA_KEYS.length);
    for (let k = 0; k < count; k++) {
      criteria[CRITERIA_KEYS[k]] = NumberInt(randInt(1, 5));
    }
    doc.criteria = criteria;
  }

  reviews.push(doc);
}
db.reviews.insertMany(reviews, { ordered: false });

// ---------------------------------------------------------------------
// Verification
// ---------------------------------------------------------------------
print('comments total:   ' + db.comments.countDocuments());
print('  top level:      ' + db.comments.countDocuments({ parentId: null }));
print('  replies:        ' + db.comments.countDocuments({ parentId: { $ne: null } }));
print('  with history:   ' + db.comments.countDocuments({ editHistory: { $exists: true } }));
print('reviews total:    ' + db.reviews.countDocuments());
print('  with criteria:  ' + db.reviews.countDocuments({ criteria: { $exists: true } }));
