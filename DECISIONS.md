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
