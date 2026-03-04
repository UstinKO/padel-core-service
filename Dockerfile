# =========================
# Stage 1: Build
# =========================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests


# =========================
# Stage 2: Runtime
# =========================
FROM eclipse-temurin:21-jre

# Устанавливаем пакеты
RUN apt-get update && \
    apt-get install -y bash tzdata curl && \
    rm -rf /var/lib/apt/lists/*

ENV TZ=Europe/Moscow

# Создаем пользователя (Debian способ)
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p logs && chown -R spring:spring /app

USER spring:spring

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]