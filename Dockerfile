# Build stage: compile Spring Boot JAR with Maven Wrapper (Java 21)
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
COPY src src

RUN chmod +x mvnw && ./mvnw -B -DskipTests package

# Runtime stage: minimal JRE image for Render
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

COPY --from=build /app/target/wallet-0.0.1-SNAPSHOT.jar app.jar
COPY --chmod=755 docker-entrypoint.sh docker-entrypoint.sh

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["./docker-entrypoint.sh"]
