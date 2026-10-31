# Dùng chung cho mọi service: docker build --build-arg SERVICE=auth-service backend/
FROM gradle:8.10.2-jdk21 AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN ./gradlew :${SERVICE}:bootJar --no-daemon -q

FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /src/${SERVICE}/build/libs/app.jar app.jar
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
