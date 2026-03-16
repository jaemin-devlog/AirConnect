# 프로필 이미지 업로드 기능 - 데이터베이스 마이그레이션 가이드

## 📋 개요

기존 `user_profiles` 테이블에 프로필 이미지 저장 기능을 추가하기 위한 마이그레이션 가이드입니다.

## ⚙️ 자동 마이그레이션 (권장)

### 로컬 환경 (H2 / MySQL)

Spring Boot의 JPA `ddl-auto: create` 또는 `ddl-auto: update` 설정으로 자동 마이그레이션됩니다.

**application-local.yml:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create  # 또는 update
```

✅ **자동으로 다음 필드가 추가됩니다:**
```
profile_image_path VARCHAR(500) NULL
```

---

## 🔧 수동 마이그레이션 (프로덕션)

### 프로덕션 환경에서 수동으로 마이그레이션:

#### MySQL 쿼리:

```sql
-- 1. 컬럼 추가 (기존 데이터가 있는 경우)
ALTER TABLE user_profiles 
ADD COLUMN profile_image_path VARCHAR(500) NULL;

-- 2. 확인
DESC user_profiles;
-- 또는
SHOW COLUMNS FROM user_profiles;
```

#### 결과 확인:
```sql
SELECT * FROM user_profiles LIMIT 1;
```

예상 결과:
| user_id | height | mbti | ... | profile_image_path | updated_at |
|---------|--------|------|-----|-------------------|------------|
| 1       | 180    | INFP | ... | NULL              | 2026-03-16 |

---

## 🚀 배포 프로세스

### Step 1: 데이터베이스 백업
```bash
# MySQL 백업
mysqldump -u root -p airconnect > airconnect_backup_$(date +%Y%m%d_%H%M%S).sql
```

### Step 2: 마이그레이션 실행
```bash
# 데이터베이스에 연결해서 위의 SQL 쿼리 실행
mysql -u root -p airconnect < migration.sql
```

### Step 3: 애플리케이션 배포
```bash
# 새 버전 배포
docker build -t airconnect:latest .
docker run -d airconnect:latest
```

### Step 4: 검증
```bash
# 업로드 디렉토리 확인
ls -la /var/lib/airconnect/profile-images

# 데이터베이스 확인
SELECT COUNT(*) FROM user_profiles;
```

---

## 📊 마이그레이션 통계

### 변경 사항:

| 항목 | 변경전 | 변경후 |
|------|--------|--------|
| user_profiles 테이블 컬럼 수 | 10개 | 11개 |
| profile_image_path | ❌ | ✅ VARCHAR(500) |
| 저장소 위치 | N/A | `/var/lib/airconnect/profile-images` |

### 기존 데이터 영향:
- ✅ 기존 사용자 데이터는 영향 없음
- ✅ profile_image_path는 NULL로 초기화
- ✅ 새 이미지 업로드 시부터 경로 저장

---

## ⏮️ 롤백 가이드

마이그레이션에 문제가 있는 경우:

### MySQL에서 롤백:
```sql
-- 컬럼 제거
ALTER TABLE user_profiles DROP COLUMN profile_image_path;

-- 확인
DESC user_profiles;
```

### 백업에서 복구:
```bash
# 백업 파일 복구
mysql -u root -p airconnect < airconnect_backup_20260316_120000.sql
```

---

## ✅ 마이그레이션 체크리스트

- [ ] 데이터베이스 백업 완료
- [ ] 마이그레이션 SQL 실행 완료
- [ ] 새 컬럼 확인 완료
- [ ] 업로드 디렉토리 생성 완료 (`mkdir -p /var/lib/airconnect/profile-images`)
- [ ] 디렉토리 권한 설정 완료 (`chmod 755`)
- [ ] 애플리케이션 배포 완료
- [ ] 테스트 완료 (이미지 업로드/다운로드)

---

## 🔍 확인 쿼리

### 마이그레이션 확인:
```sql
-- 테이블 구조 확인
DESCRIBE user_profiles;

-- 또는
SHOW COLUMNS FROM user_profiles;

-- NULL 값 확인
SELECT COUNT(*) FROM user_profiles WHERE profile_image_path IS NOT NULL;
```

### 예상 결과:
```
Field                  | Type         | Null | Key | Default | Extra
-----------------------|--------------|------|-----|---------|-------
user_id               | bigint       | NO   | PRI | NULL    |
height                | int          | YES  |     | NULL    |
mbti                  | varchar(10)  | YES  |     | NULL    |
smoking               | varchar(20)  | YES  |     | NULL    |
military              | varchar(255) | YES  |     | NULL    |
religion              | varchar(50)  | YES  |     | NULL    |
residence             | varchar(100) | YES  |     | NULL    |
intro                 | varchar(500) | YES  |     | NULL    |
instagram             | varchar(200) | YES  |     | NULL    |
profile_image_path    | varchar(500) | YES  |     | NULL    | ✅ NEW
updated_at            | datetime(6)  | NO   |     | NULL    |
```

---

## 📝 마이그레이션 SQL 파일

`migration.sql` 파일로 저장하여 사용할 수 있습니다:

```sql
-- Migration: Add profile_image_path to user_profiles
-- Date: 2026-03-16
-- Description: 프로필 이미지 저장 기능 추가

USE airconnect;

-- 컬럼 추가
ALTER TABLE user_profiles 
ADD COLUMN profile_image_path VARCHAR(500) NULL 
COMMENT '사용자 프로필 이미지 경로';

-- 마이그레이션 로그
INSERT INTO migration_logs (name, executed_at) 
VALUES ('add_profile_image_path', NOW());

-- 확인
SELECT * FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'airconnect' 
AND TABLE_NAME = 'user_profiles' 
AND COLUMN_NAME = 'profile_image_path';
```

---

## 🐳 Docker 볼륨 설정

프로덕션 배포 시 이미지 디렉토리를 볼륨으로 마운트하세요:

### Docker Compose 예제:
```yaml
version: '3.8'

services:
  airconnect:
    image: airconnect:latest
    ports:
      - "8080:8080"
    environment:
      PROFILE_IMAGE_DIR: /var/lib/airconnect/profile-images
      PROFILE_IMAGE_URL_BASE: https://api.airconnect.app/api/v1/users/profile-images
    volumes:
      - profile-images:/var/lib/airconnect/profile-images
    depends_on:
      - mysql
      - redis

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: airconnect
    volumes:
      - mysql-data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    volumes:
      - redis-data:/data

volumes:
  profile-images:
  mysql-data:
  redis-data:
```

---

## 📞 문제 해결

### 문제 1: "Table 'airconnect.user_profiles' doesn't exist"
**해결책:**
```sql
-- 테이블 존재 여부 확인
SHOW TABLES LIKE 'user_profiles';

-- 없으면 JPA가 생성하도록 대기
-- application-prod.yml에서 ddl-auto: update로 설정
```

### 문제 2: "Duplicate column name 'profile_image_path'"
**해결책:**
```sql
-- 이미 존재하면 다시 생성 불가
-- 다음 명령으로 확인
DESCRIBE user_profiles;

-- 이미 있으면 스킵
```

### 문제 3: 마이그레이션 후 이미지 다운로드 실패
**해결책:**
```bash
# 디렉토리 권한 확인
ls -la /var/lib/airconnect/profile-images

# 권한 수정 필요시
sudo chmod 755 /var/lib/airconnect/profile-images
sudo chown app-user:app-user /var/lib/airconnect/profile-images
```

---

## 🎯 마이그레이션 완료 확인

모든 작업이 완료되었는지 확인하세요:

```bash
# 1. 애플리케이션 실행 확인
curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer TOKEN"

# 2. 이미지 업로드 테스트
curl -X POST http://localhost:8080/api/v1/users/profile-image \
  -H "Authorization: Bearer TOKEN" \
  -F "file=@test.jpg"

# 3. 이미지 다운로드 테스트
curl http://localhost:8080/api/v1/users/profile-images/FILENAME -o downloaded.jpg

# 4. 데이터베이스 확인
SELECT * FROM user_profiles WHERE user_id = 1;
```

---

## 📋 버전 정보

- **기능 출시일**: 2026-03-16
- **구현 버전**: 0.0.1-SNAPSHOT
- **Spring Boot**: 3.4.3
- **Java**: 17
- **MySQL**: 8.0+

---

✅ **마이그레이션 완료!**

이제 사용자들이 프로필 이미지를 업로드하고 다운로드할 수 있습니다.

