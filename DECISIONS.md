# Decisions

Technical choices for this project: what was chosen, what was rejected, and why.

## 2026-10-23: Maven, through the Maven Wrapper

**Chose:** Maven, run with `./mvnw`.
**Rejected:** Gradle.
**Why:** Maven's `pom.xml` is declarative and easy to read, and it is the most common build setup in Spring Boot projects. The wrapper pins the Maven version inside the repo, so every machine, CI included, builds with the same version without installing Maven first.

## 2026-10-23: Java 25 and Spring Boot 4.1

**Chose:** Java 25, the current long-term-support (LTS) release, and Spring Boot 4.1.
**Rejected:** Java 21 with Spring Boot 3.x.
**Why:** A new project has no old code to stay compatible with. Starting on the current LTS and the current major version of Spring Boot avoids a migration later, and both receive fixes the longest.

## 2026-10-23: Actuator for the health endpoint

**Chose:** Spring Boot Actuator's built-in `/actuator/health`.
**Rejected:** A hand-written `/health` controller.
**Why:** Actuator is the standard way to report health in Spring apps, and it grows with the app: once PostgreSQL is added, the same endpoint also checks that the database is reachable, with no extra code. Docker and AWS health checks can poll it to decide whether the app is up.

## 2026-10-23: Health test runs a real server

**Chose:** `@SpringBootTest` with a random port, calling the endpoint over real HTTP with `RestTestClient`.
**Rejected:** MockMvc, which calls controllers without starting a server.
**Why:** The test should prove what a real client sees: the server starts, listens, and answers. A random port means the test never collides with a copy of the app already running on 8080. MockMvc is faster and will suit the many controller tests in later milestones.

## 2026-10-23: PostgreSQL 18, run with Docker Compose

**Chose:** The official `postgres:18` image, defined in `compose.yaml` and started by Spring Boot's Docker Compose support when the app starts.
**Rejected:** Installing PostgreSQL on the laptop with Homebrew; PostgreSQL 19.
**Why:** The compose file pins the database version inside the repo, so every machine runs the same database with one command and nothing to install besides Docker. The `18` tag picks up minor releases (bug and security fixes) but never moves to a new major version on its own. PostgreSQL 19 is still a release candidate, with general availability planned for November 2026, so moving to it later will be a deliberate change. Docker picks a free host port for the database instead of 5432, and Spring Boot reads which port it got, so it never collides with another project's database already on 5432. That port is published on 127.0.0.1 only, so other machines on the network cannot reach the database; that is why plain development credentials in the file are acceptable. Production credentials will come from the environment, never from the repo.

## 2026-10-23: Health endpoint lists its components

**Chose:** `management.endpoint.health.show-components: always`, so `/actuator/health` lists each check (`db`, `diskSpace`, `ping` and so on) with its own status.
**Rejected:** The default, which shows only the overall status; `show-details: always`, which also exposes disk paths and sizes and details about the database.
**Why:** When the app reports DOWN, the response should say which part failed. Component names and statuses reveal nothing sensitive. Details can be turned on for signed-in administrators once the app has authentication.

## 2026-10-23: Tests use Testcontainers, not an in-memory database

**Chose:** Testcontainers starts a fresh `postgres:18` container for the test run, and `@ServiceConnection` points the app at it.
**Rejected:** H2, an in-memory database; reusing the Docker Compose database.
**Why:** H2 only imitates PostgreSQL, so a query can pass against H2 and fail against the real database. Testing against the same engine and version as development rules that out. A container of its own means every test run starts from an empty database, untouched by development data. GitHub Actions runners have Docker, so the same tests will run in CI. The cost: `./mvnw test` needs Docker running, and the first run downloads the image.

## 2026-10-23: GitHub Actions for CI

**Chose:** A GitHub Actions workflow that runs `./mvnw verify` on every push to `main` and every pull request.
**Rejected:** Jenkins, which needs a server of its own, and CircleCI, a separate service to connect; running only `./mvnw test`.
**Why:** The workflow lives in the repo next to the code, runs free for public repositories, and its runners come with Docker, so the Testcontainers tests run unchanged. `verify` runs the tests and also builds the runnable jar, so a packaging problem fails the build too. The runner uses Temurin 25, the same JDK as development, and caches Maven downloads between runs. The workflow can only read the repo (`permissions: contents: read`), which limits the damage if a step were ever compromised.

## 2026-10-24: Flyway migrations own the database schema

**Chose:** Flyway, with numbered plain-SQL files in `src/main/resources/db/migration` that run in order when the app starts.
**Rejected:** Liquibase; letting Hibernate create the tables.
**Why:** Every schema change is a file in git, reviewed like code and applied in the same order on a laptop, in CI and in production. Liquibase's XML or YAML changelogs pay off when one app supports several kinds of database, and this one only uses PostgreSQL. Tables generated by Hibernate hide the schema and cannot safely change a database that already holds data.

## 2026-10-24: The database enforces its own rules

**Chose:** Constraints in the schema itself: email unique and stored in lowercase; application status limited to five values by a check constraint; applications tied to their user by a foreign key that deletes them with the user; an index on `applications.user_id`; auto-incrementing `bigint` ids.
**Rejected:** Checking these rules only in Java; a PostgreSQL enum type for status; UUID ids.
**Why:** The app will validate input too, but the database is the last line of defense: no bug or hand-written SQL can create two accounts for one email or an application with a made-up status. A check constraint can be changed by a later migration, while values cannot be removed from a PostgreSQL enum type. Every applications query filters by user, and PostgreSQL does not index foreign key columns on its own. Plain numeric ids are the simplest fit for JPA, and guessing one gains nothing, because every lookup also checks the owner.

## 2026-10-24: Spring Data JPA (Hibernate) for database access

**Chose:** Spring Data JPA with Hibernate. Hibernate only validates the schema at startup (`ddl-auto: validate`), and open-session-in-view is off.
**Rejected:** Spring Data JDBC; hand-written SQL everywhere with `JdbcClient`.
**Why:** JPA is the standard Java persistence API and what most Spring codebases use, and repositories turn method names like `existsByEmail` into queries. Flyway still owns the schema; Hibernate only checks at startup that the Java classes match the tables, so a mismatch fails immediately instead of at the first query. With open-session-in-view off, database access happens only inside the service layer, never lazily while the response is being written.

## 2026-10-24: Passwords hashed with Argon2id

**Chose:** Argon2id through Spring Security's `Argon2PasswordEncoder`, at OWASP's minimum settings (19 MiB of memory, 2 iterations, parallelism 1), wrapped in a `DelegatingPasswordEncoder` so every stored hash starts with `{argon2}`.
**Rejected:** bcrypt, Spring Security's default; encrypting passwords.
**Why:** Hashing is one-way. Login hashes the typed password and compares the result, so a stolen database never reveals the passwords themselves, while an encrypted password can be decrypted by anyone who also gets the key. OWASP's Password Storage Cheat Sheet lists Argon2id first and places bcrypt under legacy systems. Argon2id is memory-hard, so every guess costs an attacker memory as well as time, which blunts GPU cracking. The `{argon2}` prefix records which algorithm made each hash, so the algorithm can change later without breaking existing accounts. The cost is one extra dependency, BouncyCastle, which Spring's Argon2 implementation needs.

## 2026-10-24: Registration rules

**Chose:** Emails trimmed and lowercased before saving; passwords of 15 to 128 characters with no required symbols or digits; a duplicate email returns `409 Conflict`; errors use the RFC 9457 problem details format.
**Rejected:** The older 8-character minimum with required character types.
**Why:** NIST SP 800-63B-4 requires at least 15 characters for a password that is the only login factor, asks that at least 64 be allowed, and forbids rules forcing character types, because long passphrases are both stronger and easier to remember. The duplicate check in Java gives a clear error message; the unique constraint in the database still catches two sign-ups racing each other. Problem details is a standard JSON error format, so the frontend can handle every error the same way.

## 2026-10-24: JWT bearer tokens for the API

**Chose:** On login the API issues a JSON Web Token signed with HS256, valid for one hour, holding the user's id and email. Spring Security's OAuth2 resource server checks the signature, expiry and issuer of every request. The signing key comes from the `JWT_SECRET` environment variable; when it is unset (local development, tests), the app generates a random key at startup.
**Rejected:** Server-side sessions with a cookie; random tokens stored in the database; long-lived tokens.
**Why:** Checking a JWT needs no database lookup, because the signature proves this server issued it, so any copy of the app can verify a request. The cost is that a single token cannot be cancelled before it expires, which is why tokens last one hour. HS256 with one shared secret fits because the same app both issues and checks tokens; RSA key pairs pay off once other services need to verify them. The random-key fallback fails safe: a forgotten secret makes tokens stop working after a restart instead of signing them with a key anyone could read in the repo. CSRF protection is off because the token travels in the `Authorization` header, which a browser never attaches on its own the way it does cookies.

## 2026-10-24: Login does not reveal which emails have accounts

**Chose:** A wrong password and an unknown email get the same `401` and message, and an unknown email still runs a full password check against a placeholder hash.
**Rejected:** A "no account with that email" message.
**Why:** A different message, or a faster reply when there is no account to check, would let anyone discover who uses the app.
