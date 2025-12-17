# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /workspace

# Copy only build descriptors first (better caching)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle

# Pre-fetch dependencies (cache-friendly)
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon dependencies || true

# Copy sources and build
COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon test bootJar --stacktrace --info --console=plain

FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app
EXPOSE 8080
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
