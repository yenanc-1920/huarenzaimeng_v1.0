FROM node:24-alpine AS admin-web-build

WORKDIR /workspace/apps/admin-web
COPY apps/admin-web/package.json apps/admin-web/package-lock.json ./
RUN npm ci
COPY apps/admin-web/index.html apps/admin-web/tsconfig.json apps/admin-web/vite.config.ts ./
COPY apps/admin-web/src src
COPY apps/admin-web/scripts scripts
COPY apps/miniapp/src/static/logo.png /workspace/apps/miniapp/src/static/logo.png
ENV VITE_ADMIN_DATA_MODE=PROJECT_API_PROXY
RUN npm run test:contracts && npm run build

FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY Dockerfile Dockerfile
COPY .mvn/settings.xml .mvn/settings.xml
COPY pom.xml ./
COPY modules/core/pom.xml modules/core/pom.xml
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/worker/pom.xml apps/worker/pom.xml
COPY tools/SurefireSafeFailureSummary.java tools/SurefireSafeFailureSummary.java

RUN mvn -B -ntp -s .mvn/settings.xml -pl apps/api -am dependency:go-offline

COPY modules/core/src modules/core/src
COPY apps/api/src apps/api/src
COPY apps/api/manifests/DATA-INTEGRATION-01-readonly-challenge.txt apps/api/manifests/DATA-INTEGRATION-01-readonly-challenge.txt
COPY --from=admin-web-build /workspace/apps/admin-web/dist apps/api/src/main/resources/static
COPY apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1 apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1
COPY apps/api/Invoke-P021TestReadonlyIntegrationFinalRun.ps1 apps/api/Invoke-P021TestReadonlyIntegrationFinalRun.ps1
COPY apps/miniapp/scripts/collect-p021-it-page-actual.ps1 apps/miniapp/scripts/collect-p021-it-page-actual.ps1

RUN log=/tmp/maven-test.log; trap 'rm -f "$log"' EXIT HUP INT TERM; set +e; mvn -B -ntp -s .mvn/settings.xml -pl apps/api -am clean test -Dsurefire.useFile=true -Dsurefire.redirectTestOutputToFile=true -Dsurefire.printSummary=false >"$log" 2>&1; status=$?; set -e; rm -f "$log"; trap - EXIT HUP INT TERM; if [ "$status" -ne 0 ]; then set +e; summary=$(java tools/SurefireSafeFailureSummary.java modules/core/target/surefire-reports apps/api/target/surefire-reports 2>/dev/null); summary_status=$?; set -e; if [ "$summary_status" -eq 0 ]; then printf '%s\n' "$summary"; else echo 'SAFE_SUREFIRE_SUMMARY_ERROR SUMMARIZER_FAILED'; fi; exit "$status"; fi; echo MAVEN_TESTS_PASSED
RUN mvn -B -ntp -s .mvn/settings.xml -pl apps/api -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# The API does not need operating-system privileges. Keep a stable numeric
# identity so the same least-privilege boundary is preserved by CloudBase and
# other container runtimes.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app

COPY --from=build --chown=10001:10001 /workspace/apps/api/target/api-*.jar app.jar

ENV SERVER_PORT=8080
# Fail closed by default. The Cloud Hosting service must explicitly set
# SPRING_PROFILES_ACTIVE=release-mysql after the readiness gate.
ENV SPRING_PROFILES_ACTIVE=mock
EXPOSE 8080

USER 10001:10001
ENTRYPOINT ["java", "-Dloader.main=com.huarenzaimeng.api.FlywayV12FunctionVerificationLauncher", "-cp", "/app/app.jar", "org.springframework.boot.loader.launch.PropertiesLauncher"]
