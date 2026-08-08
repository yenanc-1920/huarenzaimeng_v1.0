FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY Dockerfile Dockerfile
COPY pom.xml ./
COPY modules/core/pom.xml modules/core/pom.xml
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/worker/pom.xml apps/worker/pom.xml

RUN mvn -B -ntp -pl apps/api -am dependency:go-offline

COPY modules/core/src modules/core/src
COPY apps/api/src apps/api/src
COPY apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1 apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1
COPY apps/api/Invoke-P021TestReadonlyIntegrationFinalRun.ps1 apps/api/Invoke-P021TestReadonlyIntegrationFinalRun.ps1
COPY apps/miniapp/scripts/collect-p021-it-page-actual.ps1 apps/miniapp/scripts/collect-p021-it-page-actual.ps1

RUN mvn -B -ntp -pl apps/api -am clean test
RUN mvn -B -ntp -pl apps/api -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# The API does not need operating-system privileges. Keep a stable numeric
# identity so the same least-privilege boundary is preserved by CloudBase and
# other container runtimes.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app

COPY --from=build --chown=10001:10001 /workspace/apps/api/target/api-*.jar app.jar

ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=mock
EXPOSE 8080

USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
