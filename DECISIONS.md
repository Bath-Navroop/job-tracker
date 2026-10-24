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
