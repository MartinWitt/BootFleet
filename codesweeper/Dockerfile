# codesweeper can't use this repo's usual native-image buildpack (spring-native-parent):
# unlike the other apps, it needs a real `mvn` and `git` on PATH at *runtime*, not just to
# build itself - MavenClient/GitClient shell out to them against a cloned checkout of the
# repo it's fixing, to verify each candidate fix (spotless + tests) before opening a PR.

# Stage 1: build codesweeper's own jar. Needs the whole reactor, not just this module -
# spring-boot-utils is a local sibling module (see root pom.xml <modules>), not a published
# artifact, so -am (also-make) requires the full multi-module source tree in the build context.
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY . .
RUN mvn -pl codesweeper -am package -DskipTests --no-transfer-progress

# Stage 2: runtime. Same maven+JDK base as the builder (not a lean JRE image) because the
# app needs mvn available at runtime, plus git (not bundled in the maven image by default).
FROM maven:3.9-eclipse-temurin-25
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /app/codesweeper/target/codesweeper-*.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
