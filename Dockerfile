# Build stage: compiles the app with the Maven Wrapper, so building the image needs nothing but Docker.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

# Dependencies first, in their own layer: Docker reuses it until pom.xml changes,
# so a code change doesn't download everything again.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw --batch-mode --quiet dependency:go-offline

# Tests run before the image is built (locally and in CI), so they are skipped here.
COPY src/main src/main
RUN ./mvnw --batch-mode package -Dmaven.test.skip=true \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime stage: only a Java runtime and the app, running as an unprivileged user.
FROM eclipse-temurin:25-jre-noble
WORKDIR /application

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# One layer per part of the app, from least to most often changed. A code change only
# replaces the small last layer, so pushing and pulling a new version is quick.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER app
EXPOSE 8080

# By default the JVM gives the heap only a quarter of the container's memory.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
