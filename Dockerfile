# syntax=docker/dockerfile:1.7
# One multi-stage Dockerfile for every Spring Boot module:
#   docker build --build-arg MODULE=weather-service -t skycast/weather-service .
# Stage 1 builds only that module (+ the modules it depends on, -am) with Maven;
# stage 2 is a small JRE image running as a non-root user.

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace

# Copy POMs first so the dependency download layer is cached until a pom.xml changes.
COPY pom.xml .
COPY weather-common/pom.xml weather-common/
COPY discovery-server/pom.xml discovery-server/
COPY auth-service/pom.xml auth-service/
COPY weather-service/pom.xml weather-service/
COPY alert-service/pom.xml alert-service/
COPY api-gateway/pom.xml api-gateway/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am dependency:go-offline -DskipTests || true

COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am package -DskipTests -Pdocker \
 && cp ${MODULE}/target/${MODULE}-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S skycast && adduser -S skycast -G skycast
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER skycast
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
