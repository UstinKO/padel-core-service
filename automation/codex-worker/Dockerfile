FROM eclipse-temurin:21-jdk-jammy AS java
FROM node:22-bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
COPY --from=java /opt/java/openjdk /opt/java/openjdk
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 git gh curl ca-certificates chromium procps postgresql-client \
    && rm -rf /var/lib/apt/lists/*
RUN npm install --global @openai/codex@0.165.0 pnpm@10
RUN useradd --create-home --uid 10001 --shell /bin/bash padel-codex \
    && mkdir -p /var/lib/padel-codex /opt/padel-codex \
    && chown padel-codex:padel-codex /var/lib/padel-codex /opt/padel-codex
COPY --chown=padel-codex:padel-codex telegram-relay/ /opt/padel-codex/automation/telegram-relay/
COPY --chown=padel-codex:padel-codex codex-worker/entrypoint.sh /opt/padel-codex/entrypoint.sh
USER padel-codex
WORKDIR /opt/padel-codex/automation/telegram-relay
ENTRYPOINT ["/bin/sh", "/opt/padel-codex/entrypoint.sh"]
