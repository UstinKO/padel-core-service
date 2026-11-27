# =========================
# Stage 1: Build
# =========================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY padel-api-contract/pom.xml padel-api-contract/pom.xml
COPY padel-bootstrap/pom.xml padel-bootstrap/pom.xml
COPY padel-tournament-engines/pom.xml padel-tournament-engines/pom.xml
COPY padel-application/pom.xml padel-application/pom.xml
COPY padel-persistence/pom.xml padel-persistence/pom.xml
RUN mvn dependency:go-offline -B

COPY frontend ./frontend
COPY padel-api-contract ./padel-api-contract
COPY padel-bootstrap ./padel-bootstrap
COPY padel-tournament-engines ./padel-tournament-engines
COPY padel-application ./padel-application
COPY padel-persistence ./padel-persistence
COPY src ./src
RUN mvn clean package -DskipTests


# =========================
# Stage 2: Runtime
# =========================
FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a

ENV TZ=Europe/Moscow

# Создаем пользователя (Debian способ)
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

COPY --from=builder /app/padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p logs && chown -R spring:spring /app

USER spring:spring

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
