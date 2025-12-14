# syntax=docker/dockerfile:1.4
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Copy common-aspects jar and install it to Maven cache
COPY libs/common-aspects-1.0.0.jar /tmp/common-aspects-1.0.0.jar
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    mvn install:install-file \
    -Dfile=/tmp/common-aspects-1.0.0.jar \
    -DgroupId=com.library \
    -DartifactId=common-aspects \
    -Dversion=1.0.0 \
    -Dpackaging=jar \
    -B
# Download dependencies
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    mvn dependency:go-offline -B || mvn dependency:resolve -B || true
COPY src ./src
# Build the service
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3004
ENTRYPOINT ["java", "-jar", "app.jar"]




