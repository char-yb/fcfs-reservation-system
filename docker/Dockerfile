# syntax=docker/dockerfile:1.7

FROM amazoncorretto:25-alpine-jdk AS builder

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY apps ./apps
COPY storage ./storage
COPY external ./external

RUN ./gradlew --no-daemon :apps:api:bootJar

FROM amazoncorretto:25-alpine-jdk

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_OPTS=""

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder /workspace/apps/api/build/libs/*.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
