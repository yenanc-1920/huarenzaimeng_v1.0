FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY modules/core/pom.xml modules/core/pom.xml
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/worker/pom.xml apps/worker/pom.xml

RUN mvn -B -ntp -pl apps/api -am dependency:go-offline

COPY modules/core/src modules/core/src
COPY apps/api/src apps/api/src

RUN mvn -B -ntp -pl apps/api -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /workspace/apps/api/target/api-*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
