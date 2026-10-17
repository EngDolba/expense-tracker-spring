# One image: the React app is built, embedded in the Spring Boot jar and
# served from the same origin as the API.

FROM node:22-bookworm-slim AS frontend
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate
WORKDIR /build/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
# Tests run in CI (they need no Docker); the image build only packages.
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home ledger
WORKDIR /app
COPY --from=backend /build/backend/target/ledger-*.jar /app/app.jar
USER 10001
EXPOSE 8080
# Works with a read-only root filesystem as long as /tmp is writable (tmpfs).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp"
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
