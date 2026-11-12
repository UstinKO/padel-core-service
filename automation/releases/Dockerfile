FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
ARG RELEASE_REVISION
ARG RELEASE_JAR_SHA256
LABEL org.opencontainers.image.revision=$RELEASE_REVISION com.padel.jar.sha256=$RELEASE_JAR_SHA256
WORKDIR /app
ENV TZ=Europe/Moscow
COPY --chmod=0644 app.jar app.jar
RUN mkdir logs && chown 100:101 logs
USER 100:101
ENTRYPOINT ["java", "-jar", "app.jar"]
