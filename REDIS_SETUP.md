# 🔴 Redis 연결 실패 해결 가이드

## 📋 문제 원인

```
org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis
```

RefreshToken을 Redis에 저장하려고 하는데 Redis 서버가 실행되지 않음.

---

## 🔧 해결 방법 (3가지)

### ✅ 방법 1️⃣: Docker로 Redis 실행 (추천! 가장 쉬움)

#### Step 1: Docker 설치 확인
```bash
docker --version
# Docker version 20.10.x 이상
```

#### Step 2: Redis 컨테이너 실행
```bash
docker run -d -p 6379:6379 --name airconnect-redis redis:7-alpine
```

#### Step 3: Redis 실행 확인
```bash
docker ps | grep airconnect-redis
```

#### Step 4: 앱 재시작
앱이 자동으로 Redis에 연결됩니다.

---

### ✅ 방법 2️⃣: Homebrew로 Redis 설치 & 실행 (Mac)

#### Step 1: Redis 설치
```bash
brew install redis
```

#### Step 2: Redis 서버 백그라운드 시작
```bash
brew services start redis
```

#### Step 3: Redis 실행 확인
```bash
redis-cli ping
# PONG (응답이 오면 성공)
```

#### Step 4: 앱 재시작

---

### ✅ 방법 3️⃣: Testcontainers로 테스트 (개발 환경)

`build.gradle`에 추가:
```gradle
testImplementation 'org.testcontainers:testcontainers:1.19.0'
testImplementation 'org.testcontainers:junit-jupiter:1.19.0'
testImplementation 'org.testcontainers:redis:1.19.0'
```

테스트 클래스:
```java
@SpringBootTest
@Testcontainers
class AuthServiceTest {
    
    @Container
    static GenericContainer<?> redis = 
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
    
    // 테스트 코드
}
```

---

## 🚀 빠른 해결 (Docker 버전)

### macOS에서 한 줄로 실행:
```bash
docker run -d -p 6379:6379 --name airconnect-redis redis:7-alpine && echo "✅ Redis 실행 완료"
```

### Redis 중지:
```bash
docker stop airconnect-redis
```

### Redis 재시작:
```bash
docker start airconnect-redis
```

### Redis 삭제:
```bash
docker rm airconnect-redis
```

---

## ✅ 연결 확인

앱 로그에서 다음을 확인:
```
INFO  org.springframework.boot.autoconfigure.data.redis.RedisProperties
      : Connecting to Redis at 127.0.0.1:6379
```

---

## 🧪 Postman 다시 테스트

Redis가 실행되면 다시 시도:

```
POST http://localhost:8080/api/v1/auth/social/login

{
  "provider": "APPLE",
  "socialToken": "{identityToken}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**성공 응답:**
```json
{
  "accessToken": "...",
  "refreshToken": "..."
}
```

---

## 📊 현재 상태

| 항목 | 상태 |
|------|------|
| Backend 코드 | ✅ 완료 |
| JSON 역직렬화 | ✅ 수정됨 |
| 빌드 | ✅ 성공 |
| **Redis 서버** | ❌ 필요 |
| Postman 테스트 | ⏳ Redis 실행 대기 |

---

## 💡 권장사항

**개발 환경:** Docker 사용 (방법 1)
- 간편함
- 격리된 환경
- 언제든 중지/시작 가능

**프로덕션 환경:** Redis 클라우드 서비스 사용
- AWS ElastiCache
- Redis Labs Cloud
- Azure Cache for Redis

---

## 🎯 다음 단계

1. Docker로 Redis 실행
2. 앱 재시작
3. Postman 테스트
4. 성공! ✅


