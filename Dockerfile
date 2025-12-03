FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy Maven settings for better network handling
COPY .mvn/settings.xml /root/.m2/settings.xml
COPY pom.xml .
# Download dependencies first (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B || mvn dependency:resolve -B
COPY src ./src
# Build with retries for network issues
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3004
ENTRYPOINT ["java", "-jar", "app.jar"]




