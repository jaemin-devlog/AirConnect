FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx768m clean bootJar -x test

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /var/lib/airconnect/profile-images && \
    chmod 755 /var/lib/airconnect/profile-images

COPY --from=builder /app/build/libs/*.jar /app/
RUN find /app -name "*plain.jar" -delete && \
    mv /app/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
