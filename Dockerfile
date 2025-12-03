# syntax=docker/dockerfile:1.4
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy Maven settings for better network handling
COPY .mvn/settings.xml* /root/.m2/settings.xml
COPY pom.xml .
# Download dependencies using shared cache mount
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    mvn dependency:go-offline -B || mvn dependency:resolve -B
COPY src ./src
# Build using shared cache mount
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3004
ENTRYPOINT ["java", "-jar", "app.jar"]




