# Job Tracker

[![CI](https://github.com/Bath-Navroop/job-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/Bath-Navroop/job-tracker/actions/workflows/ci.yml)

A web app that pulls job postings from public job boards every day, ranks them against a resume, and tracks applications from saved to offer.

**Status:** early development. The Spring Boot service runs, connects to PostgreSQL, and reports its own health and the database's; the ingestion pipeline and frontend come next.

## Tech stack

- Java 25, Spring Boot 4.1
- PostgreSQL 18, run locally with Docker Compose
- Flyway for database migrations
- Spring Data JPA (Hibernate) for database access
- Argon2id password hashing (Spring Security crypto)
- Maven (through the Maven Wrapper, so no separate Maven install is needed)
- JUnit 5 and Testcontainers for tests
- GitHub Actions for CI: every push to `main` and every pull request builds the app and runs the tests

Planned: pgvector, a Docker image for the app, React and TypeScript, AWS.

## Running locally

Requires JDK 25 and Docker, with Docker running.

```bash
./mvnw spring-boot:run
```

On startup, Spring Boot starts the PostgreSQL container defined in `compose.yaml`, and it stops the container on shutdown. Docker publishes the database on a free port on 127.0.0.1, and Spring Boot finds that port on its own. The data lives in a Docker volume, so it survives restarts.

Then, in another terminal:

```bash
curl http://localhost:8080/actuator/health
# {"components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"},...},"status":"UP"}
```

To open a SQL prompt on the database:

```bash
docker compose exec postgres psql -U jobtracker
```

To connect a database GUI instead, find the port Docker picked:

```bash
docker compose port postgres 5432
```

To delete the database and start fresh:

```bash
docker compose down -v
```

## Running the tests

Requires Docker, with Docker running.

```bash
./mvnw test
```

The tests start their own throwaway PostgreSQL container with Testcontainers, separate from the one in `compose.yaml`, so they never touch development data.

## API

| Method | Path | What it does |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Creates an account from an email and a 15 to 128 character password. Returns `201` with the account's id and email, or `409` if the email is already registered. |

Errors use the standard [problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON format. To try it with the app running:

```bash
curl -i http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email": "you@example.com", "password": "a long passphrase you will remember"}'
```

## Design decisions

See [DECISIONS.md](DECISIONS.md).
