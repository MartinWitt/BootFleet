# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25 as builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app
COPY --from=builder /app/target/image-update-detector-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseStringDeduplication", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
