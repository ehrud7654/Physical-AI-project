FROM gradle:9-jdk21-alpine AS builder
WORKDIR /workspace
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /data/ssafy-gpu \
    && chown -R spring:spring /app /data/ssafy-gpu
COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar
USER spring:spring
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
