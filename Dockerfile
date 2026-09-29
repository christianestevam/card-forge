# syntax=docker/dockerfile:1
# Imagem de qualquer serviço: docker build --build-arg SERVICE=card-service .
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY cardforge-platform/pom.xml cardforge-platform/
COPY product-service/pom.xml product-service/
COPY cardholder-service/pom.xml cardholder-service/
COPY card-service/pom.xml card-service/
COPY cardforge-platform/src cardforge-platform/src
ARG SERVICE
COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl ${SERVICE} -am package -DskipTests -Djacoco.skip=true

FROM eclipse-temurin:21.0.12.1_1-jre-alpine-3.24
ARG SERVICE
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
COPY --from=build /src/${SERVICE}/target/${SERVICE}.jar app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=30 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
