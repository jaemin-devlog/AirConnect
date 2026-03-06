# ---------- build stage ----------
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY . .

RUN gradle clean build -x test

# ---------- run stage ----------
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
