FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
ARG JAR_SHA256
LABEL com.padel.swarm-lab="true" com.padel.jar.sha256=$JAR_SHA256
WORKDIR /app
COPY app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
