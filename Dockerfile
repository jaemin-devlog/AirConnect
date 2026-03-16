FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle clean bootJar -x test

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

RUN mkdir -p /var/lib/airconnect/profile-images && \
    chmod 755 /var/lib/airconnect/profile-images

COPY --from=builder /app/build/libs/*.jar /app/
RUN find /app -name "*plain.jar" -delete && \
    mv /app/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
