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

## 2026-10-24: Applications belong to the account in the token

**Chose:** An application's owner is the user id inside the signed login token. Every applications query filters by that id, and the entity stores it as a plain `userId` field.
**Rejected:** Reading a user id from the request; a `@ManyToOne User` association on the entity.
**Why:** The caller controls everything in the request body and URL, so a user id taken from there would let anyone read or write someone else's list. The token's signature proves who is calling. The applications code never needs the rest of the user's row, so a plain id avoids loading it, and the foreign key in the database still guarantees the user exists.

## 2026-10-24: Request and response records at the API boundary

**Chose:** Separate records for what the API accepts (`CreateApplicationRequest`) and what it returns (`ApplicationResponse`). JSON is never bound to the JPA entity directly.
**Rejected:** Accepting and returning the entity itself.
**Why:** Binding JSON to the entity would let a client set fields it must never control, such as `id`, `userId` or `createdAt` (a mass-assignment bug). Separate records also keep the JSON stable when the table changes, and the validation rules sit on the request record where they are easy to read.

## 2026-10-24: Application input rules

**Chose:** Company and job title required, at most 200 characters, with surrounding spaces removed. Posting links must start with `http://` or `https://`, at most 2,048 characters. Notes at most 10,000 characters. Status optional, `SAVED` by default, stored by name (`EnumType.STRING`).
**Rejected:** Storing status as its position in the Java enum (`EnumType.ORDINAL`); accepting any URL.
**Why:** The frontend will show posting links as clickable links, and a `javascript:` link would run code in the browser of whoever clicks it. Length limits stop one request from storing megabytes of text. Storing the status name means adding or reordering enum values never changes what existing rows mean, and the names match the database's check constraint. An unknown status is rejected with `400` before it reaches the database.

## 2026-10-24: Timestamps set by the entity

**Chose:** `created_at` and `updated_at` are set in JPA lifecycle callbacks (`@PrePersist`, `@PreUpdate`) and rounded to microseconds. Both fields declare their column type as `timestamptz`, as the migration does.
**Rejected:** Relying only on the database's `DEFAULT now()`; a database trigger for `updated_at`.
**Why:** A database default fills in the value only on insert, and the saved Java object would not see it without reading the row again. `updated_at` has to change on every update, which a default never does. The callbacks set both before the SQL runs, so the response already carries them. PostgreSQL stores microseconds, so rounding makes the value in the response identical to the stored one. The defaults stay in the schema for rows inserted by hand.

## 2026-10-24: Application list without pagination

**Chose:** `GET /api/applications` returns all of the account's applications, newest first.
**Rejected:** Pagination, for now.
**Why:** One person tracks tens to a few hundred applications, which fits comfortably in one response, and the frontend can sort and filter in the browser. Pagination can come later if lists grow that large.

## 2026-10-27: Updates replace the whole application (PUT)

**Chose:** `PUT /api/applications/{id}` with every field of the application. Company, job title and status are required; an optional field left out is cleared.
**Rejected:** `PATCH` with only the changed fields (JSON Merge Patch).
**Why:** The frontend always has the whole application in hand, both in the edit form and when a card moves on the board, so sending all of it costs nothing. A full replacement uses the same validation rules as create, and sending the same request twice leaves the same result (PUT is idempotent). With `PATCH`, a Java record cannot tell a field that was left out from one sent as `null` to clear it, so it needs wrapper types or hand-parsed JSON. Status is required on `PUT` so a client that forgets it cannot silently reset an application to `SAVED`. The cost: if the same application is edited in two tabs, the last save wins. A version column (optimistic locking) can fix that if it ever matters.

## 2026-10-27: Another user's application returns 404

**Chose:** Get, update and delete look an application up by its id and the caller's user id together (`findByIdAndUserId`). An application that does not exist and one owned by someone else both return the same `404 Application not found.`
**Rejected:** `403 Forbidden` for applications owned by someone else; loading by id first and then comparing owners in Java.
**Why:** A `403` confirms that the id exists, which tells a stranger something about other accounts. Putting the owner in the query means there is no code path that loads another user's row and then forgets to check it. Deleting twice returns `204` and then `404`; the second call changes nothing, so `DELETE` is still idempotent.

## 2026-10-27: Updates rely on Hibernate's dirty checking

**Chose:** The service loads the application inside a `@Transactional` method and changes it through one `update(...)` method on the entity; Hibernate compares it with the loaded copy and writes an `UPDATE` when the transaction commits. The entity has no setters.
**Rejected:** Calling `save()` after changing the entity; a hand-written `UPDATE` query.
**Why:** Inside a transaction, Hibernate already tracks every entity it loaded, so `save()` adds nothing. If no field actually changed, no `UPDATE` runs and `updated_at` stays the same. A single `update(...)` method, visible only inside the `application` package, means the fields can only change together and only through the service. `@PreUpdate` sets `updated_at` during that flush, so the response already carries the new time.

## 2026-10-27: Validation errors list each invalid field

**Chose:** An `ApiExceptionHandler` that extends Spring's `ResponseEntityExceptionHandler` and adds an `errors` list of `{field, message}` pairs, sorted by field, to the problem details body of a `400`. It replaces Spring Boot's own problem details handler, so `spring.mvc.problemdetails.enabled` was removed.
**Rejected:** Spring's default, which only says "Invalid request content."; a hand-written `@ExceptionHandler` for each exception type.
**Why:** The frontend can show each message under the input it belongs to. Extending `ResponseEntityExceptionHandler` keeps Spring's problem details handling for every other error (`404` for unknown paths, `405`, unreadable JSON, and our own `ErrorResponseException`s) and changes only the validation case. Spring Boot registers its own handler only when no other `ResponseEntityExceptionHandler` exists, so the property no longer did anything. Validation reports errors in no fixed order, and sorting makes the same bad request always produce the same response.

## 2026-10-27: Location header is a relative path

**Chose:** `POST /api/applications` returns `Location: /api/applications/{id}`.
**Rejected:** A full URL built from the incoming request.
**Why:** HTTP allows a relative `Location` (RFC 9110). In production the app will sit behind an AWS load balancer that handles HTTPS, so the app itself sees plain HTTP on an internal address, and a URL built from the request would point there unless extra proxy settings were added. A relative path is correct wherever the app runs.

## 2026-10-27: Login attempts are limited per address and per email

**Chose:** Two token-bucket limits, checked before the database lookup and the password hash. Per client address, every attempt counts: 10 in a row, then one more every 6 seconds. Per email, only failed attempts count, because a successful login gives its attempt back: 5 in a row, then one more every 3 minutes. Over either limit the API returns `429 Too Many Requests` with a `Retry-After` header, and the same message for both limits. Unknown emails are limited exactly like real ones. The numbers are set under `auth.rate-limit`.
**Rejected:** A per-address limit alone; locking an account after a number of failures until it is unlocked by hand; a CAPTCHA.
**Why:** The per-address limit stops one machine from guessing quickly. Because it counts every attempt, it also caps how many Argon2id hashes, each needing 19 MiB of memory, one address can make the server run. An attacker spread over many addresses gets past it, so the per-email limit caps guesses against any one account wherever they come from. Counting only failures per email means someone who logs in often is never slowed down. A hard lockout would let anyone lock a user out just by typing wrong passwords for their email; a limit that refills turns that into a short wait. Limiting unknown emails the same way keeps a `429` from revealing which emails have accounts. A token bucket allows a short burst, such as a few typos, and then a steady rate, unlike a fixed window that resets all at once and allows double the rate around each reset.

## 2026-10-27: Bucket4j and Caffeine, in memory

**Chose:** Bucket4j for the token buckets, kept in one Caffeine cache per limit, which forgets a bucket after one period without use and holds at most 10,000 buckets.
**Rejected:** A hand-written counter; keeping the counts in PostgreSQL or Redis.
**Why:** Bucket4j is the standard token-bucket library for Java. It is thread-safe without locks and uses only integer arithmetic, so there are no rounding errors. A bucket nobody has used for one period has refilled completely, so dropping it then changes nothing. The size cap keeps memory bounded; if more distinct addresses than that show up at once, some buckets are dropped early and start again full. One app server runs today, so memory is enough. With several servers, each would keep its own counts and an attacker would get one limit per server; Bucket4j can store the same buckets in PostgreSQL at that point. Counts also reset when the app restarts, which is acceptable for limits measured in minutes.

## 2026-10-27: Registration attempts are limited per address

**Chose:** Every registration attempt from one client address counts, including attempts for an email that is already registered: 10 in a row, then one more every 6 minutes. Over the limit the API returns `429` with a `Retry-After` header. Registration has its own bucket, separate from login's.
**Rejected:** No limit; a per-email limit; a CAPTCHA.
**Why:** Registration runs the same slow Argon2id hash as login, so without a limit one address could tie up the server's memory or fill the database with fake accounts. A registration for an email that already exists returns `409`, which reveals that the email has an account, so the limit also slows anyone using registration to check a list of emails. A person registers once, so a few people signing up on the same Wi-Fi still fit in the burst of 10. A per-email limit would add nothing, since each email can only be registered once. A separate bucket means a burst of sign-ups never blocks anyone from logging in.

## 2026-10-27: Client address from X-Forwarded-For, trusted only from internal addresses

**Chose:** `server.forward-headers-strategy: native`, which turns on Tomcat's `RemoteIpValve`. When a request comes from a private or loopback address, such as the HTTPS proxy in production, Tomcat takes the client's address from the `X-Forwarded-For` header.
**Rejected:** Reading `X-Forwarded-For` in our own code; leaving it off.
**Why:** In production every request reaches the app through the HTTPS proxy, so without this every user would appear to come from the proxy and all would share one login limit. A client can write anything into `X-Forwarded-For`, so Tomcat only trusts the header when the connection itself comes from an internal address; anyone connecting directly from the internet is identified by their real address. When the proxy appends the real address to a header the client already filled in, Tomcat reads the list from the right and stops at the first address that is not an internal proxy, so a forged value is ignored. The same setting makes Tomcat honor `X-Forwarded-Proto`, so the app knows when the original request used HTTPS.

## 2026-10-27: Tests raise the per-address login limit

**Chose:** `src/test/resources/config/application.yaml`, which Spring Boot loads on top of the main `application.yaml` in every test, raises both per-address limits to 1,000. `RateLimitTests` runs in its own Spring context with small limits (3 logins per address, 2 failed logins per email, 2 registrations per address), and gives each request an address from the documentation range `198.51.100.0/24` through `X-Forwarded-For`.
**Rejected:** Turning the limiter off in tests; resetting it between tests from test code.
**Why:** Every test request comes from 127.0.0.1, so the real limits of 10 would throttle the test suite itself. The rate limit tests still run the real limiter, with numbers small enough to read at a glance, and separate addresses keep each test independent. They also exercise the `X-Forwarded-For` handling, since test requests come from loopback, which Tomcat trusts. The cost is one more test context with its own PostgreSQL container, a few seconds per run.
