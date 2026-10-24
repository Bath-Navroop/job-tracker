# Job Tracker

A web app that pulls job postings from public job boards every day, ranks them against a resume, and tracks applications from saved to offer.

**Status:** early development. The Spring Boot service runs and reports its health; the database, ingestion pipeline and frontend come next.

## Tech stack

- Java 25, Spring Boot 4.1
- Maven (through the Maven Wrapper, so no separate Maven install is needed)
- JUnit 5 for tests

Planned: PostgreSQL with pgvector, React and TypeScript, Docker, GitHub Actions, AWS.

## Running locally

Requires JDK 25.

```bash
./mvnw spring-boot:run
```

Then, in another terminal:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## Running the tests

```bash
./mvnw test
```

## Design decisions

See [DECISIONS.md](DECISIONS.md).
