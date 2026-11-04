# Blogging Platform I implementation summary

The [lab brief](Blogging-Platform-I.md) defines the requirements. This table maps each epic to evidence in the current implementation.

| Epic | Implementation | Evidence |
|---|---|---|
| 1. Database design | PostgreSQL entities and keys, many-to-many tagging, MongoDB document schemas, and separate index setup. | [ERD, physical model, and 3NF](docs/database-design.md) · [Database scripts](db/) |
| 2. CRUD and data access | JavaFX post and comment CRUD, form validation, parameterized queries, and a paginated post list with keyword, author, and tag filters. | [Screenshots](README.md#screenshots) · [Controllers](src/main/java/org/example/bloggingplatformi/controller/) · [DAOs](src/main/java/org/example/bloggingplatformi/dao/) |
| 3. Searching, sorting, and optimization | Trigram search, post detail caching with invalidation on update and deletion, merge sort, and binary search. | [SQL indexes](db/postgres/indexes.sql) · [Services](src/main/java/org/example/bloggingplatformi/service/) · [Algorithms](src/main/java/org/example/bloggingplatformi/util/Sorting.java) |
| 4. Performance and NoSQL | Before-and-after timings plus MongoDB validators and indexes for comments and reviews. | [Performance report](docs/performance-report.md) · [MongoDB setup](db/mongo/setup.js) |
| 5. Reporting | Setup, configuration, screenshots, and test instructions. | [README](README.md) · [Unit tests](src/test/java/org/example/bloggingplatformi/) |

The [earlier phase log](https://github.com/josephus-mupanda/Blogging-Platform-I-Lab/blob/5353a2c50b716fe27e11312ba16547420690fa11/phases.md) records the previous implementation.
