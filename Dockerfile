# Multi-stage build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
COPY src/ src/

RUN mvn clean package -DskipTests

# Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/traced-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]