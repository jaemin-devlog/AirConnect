# 🚀 프론트엔드 배포 가이드 (Render, Vercel 등)

## 🎯 현재 상황 정리

### Backend (이미 완료) ✅
```
Java Spring Boot 애플리케이션
- 로컬: http://localhost:8080
- 배포 필요: AWS, Google Cloud, Render 등
```

### Frontend (프론트가 해야 함)
```
iOS/Android 네이티브 앱 또는 React Native
- 로컬 테스트: 시뮬레이터에서 localhost:8080 호출
- 배포: App Store / Google Play
```

---

## 📱 프론트엔드 종류별 배포 방법

### 1️⃣ iOS 네이티브 앱 (Swift)

#### 개발 중 (로컬 테스트)
```
Backend Base URL: http://localhost:8080
→ iOS 시뮬레이터에서 테스트
```

#### 배포 (App Store)
```
1. Apple Developer Account 필요
2. Code Signing 설정
3. App Store Connect에서 앱 등록
4. TestFlight로 베타 테스트
5. App Store 심사 제출
```

**참고:** iOS 앱은 "배포"라는 개념이 다름 (서버 배포 X, 앱 배포)

---

### 2️⃣ Android 네이티브 앱 (Kotlin)

#### 개발 중 (로컬 테스트)
```
Backend Base URL: http://localhost:8080
→ Android 에뮬레이터에서 테스트
```

#### 배포 (Google Play Store)
```
1. Google Play Developer Account 필요
2. APK 또는 AAB 파일 생성
3. Google Play Console에서 앱 등록
4. 심사 제출
5. 승인 후 배포
```

---

### 3️⃣ React Native 앱

#### 개발 중 (로컬 테스트)
```
Backend Base URL: http://localhost:8080
```

#### 배포
**iOS:**
- Xcode → Archive → App Store Connect

**Android:**
- Android Studio → Build → Generate Signed APK → Google Play

---

### 4️⃣ 웹 프론트엔드 (React, Vue, Angular)

만약 웹 버전도 만든다면:

#### 개발 중
```
Backend Base URL: http://localhost:8080
Frontend: http://localhost:3000 (또는 5173 등)
```

#### 배포
| 서비스 | 특징 | 가격 |
|--------|------|------|
| **Vercel** | Next.js 최적화, 매우 간편 | 무료 ~ |
| **Render** | 다양한 프레임워크 지원 | 무료 ~ |
| **Netlify** | 정적 사이트 호스팅 | 무료 ~ |
| **AWS S3 + CloudFront** | 대규모 서비스 | 저렴 |

---

## 🔧 Backend 배포 (프론트가 연동하려면)

### 문제: 프론트가 localhost:8080에 연결 불가
```
localhost는 프론트엔드가 실행되는 기기 자체를 의미
→ iOS 앱에서 localhost:8080 = 아이폰 자체의 8080 포트
→ 앱 서버가 아이폰에 없으므로 연결 실패
```

### 해결: Backend도 배포 필요!

**배포 옵션:**
1. **Render** (추천! 가장 쉬움)
2. **Railway**
3. **AWS EC2**
4. **Google Cloud**
5. **Heroku** (유료화됨)

---

## 🚀 Render로 Backend 배포하기 (가장 쉬움!)

### Step 1: Render 가입
```
https://render.com → Sign up
```

### Step 2: GitHub에 코드 push
```bash
git init
git add .
git commit -m "Apple login integration"
git remote add origin https://github.com/your-username/your-repo.git
git push -u origin main
```

### Step 3: Render에서 배포
```
1. Render Dashboard 접속
2. "New +" → "Web Service"
3. GitHub 연결
4. Repository 선택: your-repo
5. Environment: Java
6. Build Command: ./gradlew build
7. Start Command: java -jar build/libs/*.jar
8. Deploy!
```

### Step 4: 배포 후 URL 확인
```
https://your-app-name.onrender.com
```

### Step 5: 프론트에서 Base URL 변경
```swift
// Before (개발)
let baseURL = "http://localhost:8080"

// After (배포)
let baseURL = "https://your-app-name.onrender.com"
```

---

## 🔗 연결 흐름

### 개발 환경
```
iOS 시뮬레이터 → http://localhost:8080 (로컬 Backend)
```

### 배포 환경
```
iOS 앱 (App Store) → https://your-app-name.onrender.com (배포된 Backend)
```

---

## 📋 배포 체크리스트

### Backend (Java Spring Boot)
- [ ] GitHub에 코드 push
- [ ] Render 또는 Railway에 배포
- [ ] 배포된 URL 확인
- [ ] Redis 클라우드 설정 (Render에서 Redis 사용)
- [ ] 환경 변수 설정

### Frontend (iOS/Android)
- [ ] Base URL을 배포된 Backend로 변경
- [ ] 로컬 테스트 완료
- [ ] App Store / Google Play 배포
- [ ] 심사 통과 후 출시

---

## 🔑 환경 변수 설정 (중요!)

### Render 환경 변수 설정
```
SPRING_REDIS_HOST: your-redis-host.com
SPRING_REDIS_PORT: 6379
SPRING_REDIS_PASSWORD: your-password
```

### 로컬 개발
```
application.yml (localhost 사용)
```

### 배포 환경
```
Render 대시보드 → Environment → 환경 변수 설정
```

---

## 💡 Redis 배포

### 옵션 1: Render Redis (추천)
```
Render Dashboard → "New +" → "Redis"
자동으로 Backend와 연결
```

### 옵션 2: Redis Cloud (무료)
```
https://app.redislabs.com → 가입
무료 30MB 데이터베이스 생성
연결 정보를 Render 환경변수에 설정
```

### 옵션 3: AWS ElastiCache
```
프로덕션 규모 (비용 있음)
```

---

## 🎯 전체 배포 순서

```
1️⃣ Backend 배포 (Render)
   ↓
2️⃣ Redis 배포 (Render 또는 Redis Cloud)
   ↓
3️⃣ Backend 환경 변수 설정
   ↓
4️⃣ Postman으로 배포된 Backend 테스트
   ↓
5️⃣ Frontend Base URL 변경
   ↓
6️⃣ iOS 앱 배포 (App Store)
   ↓
7️⃣ Android 앱 배포 (Google Play)
   ↓
✅ 완료!
```

---

## 📊 비용 예상

| 서비스 | 무료 계획 | 유료 시작 |
|--------|---------|---------|
| **Render Backend** | $0.10/시간 → 항상 off | $7/월 |
| **Render Redis** | 포함 | - |
| **Redis Cloud** | 30MB 무료 | $15/월 |
| **iOS 배포** | 무료 (연간 $99 developer fee) | - |
| **Android 배포** | 무료 (일회 $25) | - |

---

## ⚠️ 주의사항

### 1. HTTPS 필수
```
Apple은 HTTPS 통신만 허용
localhost HTTP 테스트 후 배포 시 HTTPS로 변경 필수
```

### 2. CORS 설정
```
프론트엔드 도메인을 Backend CORS 허용 목록에 추가
(현재는 설정되어 있을 것)
```

### 3. 환경 변수 관리
```
민감한 정보 (API key, 비밀키)는 절대 코드에 하드코딩 금지
Render 환경 변수에 설정
```

---

## 🎉 결론

### 프론트가 앱 배포하려면:

✅ **필수:**
1. Backend도 배포되어야 함 (Render/Railway)
2. 배포된 Backend URL을 프론트에 설정
3. iOS/Android 앱을 App Store/Play Store에 배포

✅ **추가 배포:**
- Redis (Render Redis 또는 Redis Cloud)
- 필요시 웹 버전 (Vercel/Render)

---

## 📝 빠른 시작

```bash
# 1. Backend 배포 (Render)
# Render 대시보드에서 GitHub repo 연결

# 2. Redis 배포 (Render)
# Render 대시보드에서 Redis 생성

# 3. 환경 변수 설정 (Render)
# SPRING_REDIS_HOST, PORT, PASSWORD 설정

# 4. Frontend 연동
# Base URL: https://your-app.onrender.com

# 5. 앱 배포
# iOS: Xcode → Archive → App Store Connect
# Android: Android Studio → Build → Google Play
```

**모든 준비 완료!** 🚀


