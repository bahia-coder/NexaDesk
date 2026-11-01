# --- Build stage -------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies first: this layer is cached as long as pom.xml does not change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexadesk && adduser -S nexadesk -G nexadesk
WORKDIR /app

COPY --from=build /workspace/target/nexadesk-api-*.jar app.jar
RUN mkdir -p /app/uploads && chown -R nexadesk:nexadesk /app
USER nexadesk

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
