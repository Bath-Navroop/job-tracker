# Job Tracker

[![CI](https://github.com/Bath-Navroop/job-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/Bath-Navroop/job-tracker/actions/workflows/ci.yml)

A web app that pulls job postings from public job boards every day, ranks them against a resume, and tracks applications from saved to offer.

**Status:** early development. The API handles accounts, login, and saving, listing, updating and deleting job applications, backed by PostgreSQL; the ingestion pipeline and frontend come next.

## Tech stack

- Java 25, Spring Boot 4.1
- PostgreSQL 18, run locally with Docker Compose
- Flyway for database migrations
- Spring Data JPA (Hibernate) for database access
- Spring Security: Argon2id password hashing and JWT bearer tokens
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

Login tokens are signed with the base64 key in the `JWT_SECRET` environment variable. Locally you can leave it unset: the app then makes a random key at startup, so tokens stop working after a restart. To keep them working across restarts:

```bash
export JWT_SECRET=$(openssl rand -base64 32)
```

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
| `POST` | `/api/auth/login` | Checks an email and password. Returns `200` with a bearer token valid for one hour, or `401`. |
| `GET` | `/api/auth/me` | Returns the logged-in account's id and email. |
| `POST` | `/api/applications` | Saves a job application to the logged-in account. Company and job title are required; posting link, status (`SAVED` by default), date applied and notes are optional. Returns `201` with the saved application and its address in the `Location` header. |
| `GET` | `/api/applications` | Lists the logged-in account's applications, newest first. |
| `GET` | `/api/applications/{id}` | Returns one application. |
| `PUT` | `/api/applications/{id}` | Replaces an application with the fields sent. Company, job title and status are required; optional fields left out are cleared. Returns `200` with the updated application. |
| `DELETE` | `/api/applications/{id}` | Deletes an application. Returns `204`. |

Every endpoint except register, login and health needs the token in an `Authorization: Bearer <token>` header. An application that does not exist and one that belongs to another account both return `404`.

Errors use the standard [problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON format. When a request has invalid fields, the response lists each one:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Some fields are invalid.",
  "instance": "/api/applications",
  "errors": [
    {"field": "company", "message": "must not be blank"},
    {"field": "postingUrl", "message": "must be an http or https link"}
  ]
}
```

To try the API with the app running:

```bash
curl -i http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email": "you@example.com", "password": "a long passphrase you will remember"}'

curl -i http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email": "you@example.com", "password": "a long passphrase you will remember"}'
```

Then use the `accessToken` from the login response:

```bash
TOKEN=<accessToken from the login response>

curl -i http://localhost:8080/api/applications \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"company": "Acme", "jobTitle": "Software Engineer", "status": "APPLIED", "appliedOn": "2026-10-20"}'

curl -i http://localhost:8080/api/applications -H "Authorization: Bearer $TOKEN"
```

To change or delete one, use the `id` from the responses above (here `1`):

```bash
curl -i -X PUT http://localhost:8080/api/applications/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"company": "Acme", "jobTitle": "Software Engineer", "status": "INTERVIEW", "appliedOn": "2026-10-20"}'

curl -i -X DELETE http://localhost:8080/api/applications/1 -H "Authorization: Bearer $TOKEN"
```

Status is one of `SAVED`, `APPLIED`, `INTERVIEW`, `OFFER` or `REJECTED`, and dates use the `YYYY-MM-DD` format.

## Design decisions

See [DECISIONS.md](DECISIONS.md).
