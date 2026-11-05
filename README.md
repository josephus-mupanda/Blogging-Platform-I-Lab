# Smart Blogging Platform

A JavaFX desktop app for writing and discovering blog posts. PostgreSQL stores users, posts, and tags. MongoDB stores comments and reviews.

![Post details with comments and reviews](docs/screenshots/05-post-detail.png)

## Run locally

Install Java 21, PostgreSQL with the `pg_trgm` extension available, MongoDB, and `mongosh`. The repository includes a Maven wrapper. Run the commands from the repository root.

### 1. Set up PostgreSQL

Use a fresh database. The schema script drops and recreates the application tables. The default port for this project is 5433.

```sh
psql -U postgres -p 5433 -c "CREATE DATABASE blogging_platform;"
psql -U postgres -p 5433 -d blogging_platform -v ON_ERROR_STOP=1 -f db/postgres/schema.sql
psql -U postgres -p 5433 -d blogging_platform -v ON_ERROR_STOP=1 -f db/postgres/indexes.sql
psql -U postgres -p 5433 -d blogging_platform -v ON_ERROR_STOP=1 -f db/postgres/seed.sql
```

### 2. Set up MongoDB

```sh
mongosh "mongodb://localhost:27017/blogging_platform" --file db/mongo/setup.js
```

### 3. Launch the application

Run `./mvnw javafx:run` on macOS or Linux, or `.\mvnw.cmd javafx:run` on Windows. Sign in with the seeded local demo account (`admin` / `111111`), or select **Sign up** to create a blogger or reader account.

Use environment variables to override the defaults in [application.properties](src/main/resources/application.properties). For example, if PostgreSQL runs on port 5432, update the port in both the setup commands and `DB_POSTGRES_URL`.

| Environment variable | Default |
|---|---|
| `DB_POSTGRES_URL` | `jdbc:postgresql://localhost:5433/blogging_platform` |
| `DB_POSTGRES_USER` | `postgres` |
| `DB_POSTGRES_PASSWORD` | `postgres` |
| `DB_MONGO_URI` | `mongodb://localhost:27017/?serverSelectionTimeoutMS=3000` |
| `DB_MONGO_DATABASE` | `blogging_platform` |

## Features and roles

| Feature | Behavior |
|---|---|
| Posts | Create, edit, or delete posts with a title, body, and optional tags selected from the existing catalog. |
| Discovery | Search titles and bodies without matching case. Filter by author or tag and browse 10 posts per page in newest-first order. |
| Comments | Read and add comments. Edit or delete your own comments. |
| Reviews | Submit a 1–5 rating with optional text, once per user per post. See the average on the post detail screen. |
| Dashboard | See counts of posts, users, tags, comments, and reviews, plus recent posts and tag usage. |
| Administration | List accounts and create or delete tags. |

Readers can browse, comment, and review. Bloggers can also create posts and edit or delete their own posts. Admins can edit or delete any post or comment and can access **Tags** and **Users**.

## Screenshots

| | | |
|---|---|---|
| [Sign in](docs/screenshots/01-login.png) | [Sign up](docs/screenshots/02-sign-up.png) | [Dashboard](docs/screenshots/03-dashboard.png) |
| [Post list](docs/screenshots/04-posts.png) | [Post details](docs/screenshots/05-post-detail.png) | [Post editor](docs/screenshots/06-post-editor.png) |
| [Validation](docs/screenshots/07-validation.png) | [Tags](docs/screenshots/08-tags.png) | [Users](docs/screenshots/09-users.png) |

## Implementation and tests

FXML defines the views. Controllers call services for validation and business rules. DAOs use parameterized JDBC statements for PostgreSQL and the MongoDB driver for comments and reviews. A shared `HashMap` caches post details by ID, stores the saved version after a create or update, and removes deleted posts. The tag service uses merge sort to order tags and binary search to check for duplicate names.

Run `./mvnw test` or `.\mvnw.cmd test` on Windows. The unit tests cover validation, password hashing, sorting, and searching.

| Lab deliverable | Where to find it |
|---|---|
| Project brief | [Blogging Platform I](Blogging-Platform-I.md) |
| ERD, schema, normalization, and store choices | [Database design](docs/database-design.md) |
| PostgreSQL and MongoDB setup scripts | [Database scripts](db/) |
| Before-and-after timings and reproduction steps | [Performance report](docs/performance-report.md) |
| Requirements and implementation evidence | [Phase summary](phases.md) |
