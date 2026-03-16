# ---------- build stage ----------
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app
COPY . .

RUN gradle clean build -x test

# ---------- run stage ----------
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# 프로필 이미지 저장 디렉토리 생성
RUN mkdir -p /var/lib/airconnect/profile-images && \
    chmod 755 /var/lib/airconnect/profile-images

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
