FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew :services:inventory-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/inventory-service/build/libs/*.jar app.jar
EXPOSE 8096
ENTRYPOINT ["java", "-jar", "app.jar"]
