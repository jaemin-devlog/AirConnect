# ---------- build stage ----------
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

COPY . .

RUN gradle clean build -x test

# ---------- run stage ----------
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]