FROM node:24-alpine AS admin-web-build

WORKDIR /workspace/apps/admin-web
COPY apps/admin-web/package.json apps/admin-web/package-lock.json ./
RUN npm ci
COPY apps/admin-web/index.html apps/admin-web/tsconfig.json apps/admin-web/vite.config.ts ./
COPY apps/admin-web/src src
COPY apps/admin-web/scripts scripts
COPY apps/miniapp/src/static/logo.png /workspace/apps/miniapp/src/static/logo.png
ENV VITE_ADMIN_DATA_MODE=PROJECT_API_PROXY
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY Dockerfile Dockerfile
COPY .mvn/settings.xml .mvn/settings.xml
COPY pom.xml ./
COPY modules/core/pom.xml modules/core/pom.xml
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/worker/pom.xml apps/worker/pom.xml
RUN mvn -B -ntp -s .mvn/settings.xml -pl apps/api -am dependency:go-offline

COPY modules/core/src modules/core/src
COPY apps/api/src apps/api/src
COPY apps/api/manifests/DATA-INTEGRATION-01-readonly-challenge.txt apps/api/manifests/DATA-INTEGRATION-01-readonly-challenge.txt
COPY --from=admin-web-build /workspace/apps/admin-web/dist apps/api/src/main/resources/static
COPY apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1 apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1
COPY apps/api/Invoke-P021TestReadonlyIntegrationFinalRun.ps1 apps/api/Invoke-P021TestReadonlyIntegrationFinalRun.ps1
COPY apps/miniapp/scripts/collect-p021-it-page-actual.ps1 apps/miniapp/scripts/collect-p021-it-page-actual.ps1

RUN mvn -B -ntp -s .mvn/settings.xml -pl apps/api -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && update-ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && USE_SYSTEM_CA_CERTS=1 /__cacert_entrypoint.sh /bin/true \
    && install -d -m 0755 /app/truststore \
    && cp "$JAVA_HOME/lib/security/cacerts" /app/truststore/cacerts \
    && chmod 0444 /app/truststore/cacerts \
    && keytool -list -keystore /app/truststore/cacerts -storepass changeit >/dev/null

# The API does not need operating-system privileges. Keep a stable numeric
# identity so the same least-privilege boundary is preserved by CloudBase and
# other container runtimes.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app

COPY --from=build --chown=10001:10001 /workspace/apps/api/target/api-*.jar app.jar
COPY --chown=10001:10001 tools/container-entrypoint.sh /app/container-entrypoint.sh
RUN chmod 0555 /app/container-entrypoint.sh

ENV SERVER_PORT=8080
ENV HZ_JAVA_TRUSTSTORE_PATH=/app/truststore/cacerts
ENV JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/app/truststore/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
# No profile default is permitted in the formal image. The hosting service must
# explicitly select one supported release combination; the entrypoint fails closed otherwise.
EXPOSE 8080

USER 10001:10001
ENTRYPOINT ["/app/container-entrypoint.sh"]
