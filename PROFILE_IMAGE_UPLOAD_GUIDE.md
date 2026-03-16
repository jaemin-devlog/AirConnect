# 프로필 이미지 업로드 기능 구현 가이드

## 📋 개요

사용자 프로필에 사진을 저장하는 기능을 구현했습니다. 클라이언트에서 이미지 파일을 전송하면 서버의 디스크에 저장하고, URL을 반환합니다.

## 🔧 구현 내용

### 1. **엔드포인트**

#### 이미지 업로드
```
POST /api/v1/users/profile-image
Content-Type: multipart/form-data

Parameters:
- file: MultipartFile (필수) - 업로드할 이미지 파일
```

**요청 예시 (curl):**
```bash
curl -X POST http://localhost:8080/api/v1/users/profile-image \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "file=@/path/to/image.jpg"
```

**응답:**
```json
{
  "success": true,
  "data": {
    "imageUrl": "http://localhost:8080/api/v1/users/profile-images/20260316120530_uuid-string.jpg"
  },
  "traceId": "trace-id-value"
}
```

#### 이미지 다운로드
```
GET /api/v1/users/profile-images/{fileName}
```

**예시:**
```bash
curl http://localhost:8080/api/v1/users/profile-images/20260316120530_uuid-string.jpg -o profile.jpg
```

---

### 2. **프론트엔드 구현 예시**

#### React/JavaScript 예시:
```javascript
// 파일 선택 핸들러
async function uploadProfileImage(file) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch('/api/v1/users/profile-image', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`
    },
    body: formData
  });

  const result = await response.json();
  if (result.success) {
    console.log('이미지 업로드 성공:', result.data.imageUrl);
    // 이미지 URL을 사용하여 프로필 표시
    displayProfileImage(result.data.imageUrl);
  }
}

// HTML
<input 
  type="file" 
  accept="image/*" 
  onChange={(e) => uploadProfileImage(e.target.files[0])}
/>
```

#### Swift (iOS) 예시:
```swift
func uploadProfileImage(image: UIImage) {
    let url = URL(string: "https://api.airconnect.app/api/v1/users/profile-image")!
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
    
    let boundary = UUID().uuidString
    request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
    
    var body = Data()
    
    // 바운더리 및 헤더 추가
    body.append("--\(boundary)\r\n".data(using: .utf8)!)
    body.append("Content-Disposition: form-data; name=\"file\"; filename=\"profile.jpg\"\r\n".data(using: .utf8)!)
    body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
    
    // 이미지 데이터
    if let imageData = image.jpegData(compressionQuality: 0.8) {
        body.append(imageData)
    }
    
    body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
    
    request.httpBody = body
    
    URLSession.shared.dataTask(with: request) { data, response, error in
        if let data = data {
            let response = try? JSONDecoder().decode(APIResponse<ProfileImageUploadResponse>.self, from: data)
            print("업로드 성공: \(response?.data?.imageUrl ?? "")")
        }
    }.resume()
}
```

---

### 3. **지원하는 이미지 형식**

- JPEG (.jpg, .jpeg)
- PNG (.png)
- WebP (.webp)
- GIF (.gif)

**제약사항:**
- 최대 파일 크기: 10MB
- 컨텐츠 타입 검증 필수

---

### 4. **서버 설정 (application.yml)**

#### 로컬 환경:
```yaml
app:
  upload:
    profile-image-dir: /tmp/airconnect/profile-images
    profile-image-url-base: http://localhost:8080/api/v1/users/profile-images
```

#### 프로덕션 환경 (환경변수):
```bash
PROFILE_IMAGE_DIR=/var/lib/airconnect/profile-images
PROFILE_IMAGE_URL_BASE=https://api.airconnect.app/api/v1/users/profile-images
```

---

### 5. **데이터베이스 변경사항**

`user_profiles` 테이블에 새로운 컬럼이 추가되었습니다:

```sql
ALTER TABLE user_profiles 
ADD COLUMN profile_image_path VARCHAR(500);
```

**필드 설명:**
- `profile_image_path`: 저장된 이미지의 파일 이름 (예: `20260316120530_uuid-string.jpg`)

---

### 6. **저장소 구조**

#### 로컬 환경:
```
/tmp/airconnect/profile-images/
├── 20260316120530_uuid-string1.jpg
├── 20260316120535_uuid-string2.png
└── ...
```

#### 프로덕션 환경 (리눅스 서버):
```
/var/lib/airconnect/profile-images/
├── 20260316120530_uuid-string1.jpg
├── 20260316120535_uuid-string2.png
└── ...
```

**디렉토리 권한:**
```bash
# 서버에서 실행
mkdir -p /var/lib/airconnect/profile-images
chmod 755 /var/lib/airconnect/profile-images
```

---

### 7. **용량 계산**

**1000명 기준 저장소 계산:**

- 평균 이미지 크기: 500KB (JPEG 압축)
- 1000명 × 0.5MB = **500MB**

✅ **결론: 서버 디스크에 저장 가능**

**권장사항:**
- 서버 디스크 여유 공간: 최소 2GB 이상
- 정기적인 백업 진행
- 필요시 CDN(AWS S3, Cloudinary 등) 고려

---

### 8. **에러 처리**

| 상태코드 | 오류 | 원인 |
|---------|------|------|
| 400 | INVALID_INPUT | 파일 크기 초과 (>10MB) 또는 지원하지 않는 형식 |
| 400 | INVALID_INPUT | 파일이 비어있음 |
| 404 | USER_NOT_FOUND | 프로필이 없거나 사용자가 삭제됨 |
| 500 | 서버 오류 | 파일 저장 실패 |

**응답 예시:**
```json
{
  "success": false,
  "error": {
    "errorCode": "INVALID_INPUT",
    "message": "파일 크기가 10MB를 초과합니다"
  },
  "traceId": "trace-id-value"
}
```

---

### 9. **보안 고려사항**

✅ **구현된 보안 기능:**
- 파일 타입 검증 (MIME type)
- 파일 크기 제한 (10MB)
- 경로 조회 방지 (`../` 차단)
- 고유한 파일 이름 생성 (UUID + 타임스탐프)
- JWT 인증 필수

⚠️ **추가 권장사항:**
1. 정기적인 바이러스 스캔
2. 이미지 메타데이터 제거
3. CDN을 통한 이미지 제공
4. 이미지 리사이징 (썸네일 생성)

---

### 10. **배포 가이드**

#### Docker 환경:
```bash
# 빌드
docker build -t airconnect:latest .

# 실행 (환경변수 설정)
docker run -d \
  -p 8080:8080 \
  -e PROFILE_IMAGE_DIR=/var/lib/airconnect/profile-images \
  -e PROFILE_IMAGE_URL_BASE=https://api.airconnect.app/api/v1/users/profile-images \
  -v /var/lib/airconnect/profile-images:/var/lib/airconnect/profile-images \
  airconnect:latest
```

#### Render/Railway 배포:
```bash
# 환경 변수 설정
PROFILE_IMAGE_DIR=/tmp/profile-images
PROFILE_IMAGE_URL_BASE=https://your-app.onrender.com/api/v1/users/profile-images
```

---

### 11. **테스트 가이드**

#### Postman 테스트:
1. **Authorization** 탭에서 Bearer Token 설정
2. **Body** → **form-data**
3. Key: `file`, Type: `File` → 이미지 선택
4. **Send**

#### curl 테스트:
```bash
curl -X POST http://localhost:8080/api/v1/users/profile-image \
  -H "Authorization: Bearer JWT_TOKEN" \
  -F "file=@/path/to/image.jpg" \
  -v
```

---

### 12. **문제 해결**

**Q: "파일을 찾을 수 없습니다" 오류가 발생합니다**
- A: 업로드 디렉토리 권한 확인: `ls -la /var/lib/airconnect/`

**Q: 이미지 업로드 후 다운로드가 안 됩니다**
- A: URL 기본값 확인 및 방화벽 설정 확인

**Q: 서버를 재시작하면 업로드한 이미지가 사라집니다**
- A: 볼륨 마운트 확인 (`docker run -v` 옵션)

---

## 📝 구현된 파일 목록

### 새로 생성된 파일:
1. `src/main/java/univ/airconnect/user/service/UserProfileImageService.java`
2. `src/main/java/univ/airconnect/user/controller/ProfileImageController.java`
3. `src/main/java/univ/airconnect/user/dto/response/ProfileImageUploadResponse.java`

### 수정된 파일:
1. `src/main/java/univ/airconnect/user/domain/entity/UserProfile.java` - 이미지 경로 필드 추가
2. `src/main/java/univ/airconnect/user/dto/response/UserProfileResponse.java` - 이미지 경로 응답 추가
3. `src/main/resources/application.yml` - 업로드 설정 추가
4. `src/main/resources/application-local.yml` - 로컬 환경 설정
5. `src/main/resources/application-prod.yml` - 프로덕션 환경 설정
6. `Dockerfile` - 이미지 저장 디렉토리 생성

---

## ✅ 완료 체크리스트

- [x] 프로필 이미지 업로드 엔드포인트 구현
- [x] 이미지 다운로드 엔드포인트 구현
- [x] 파일 유효성 검증 (크기, 형식)
- [x] 고유한 파일 이름 생성
- [x] 데이터베이스 모델 업데이트
- [x] 설정 파일 업데이트
- [x] Docker 환경 설정
- [x] 에러 처리
- [x] 보안 검증

---

## 🚀 다음 단계

1. **이미지 최적화**: 업로드 시 리사이징, 압축 추가
2. **캐싱**: 이미지 응답에 캐시 헤더 추가
3. **CDN 연동**: AWS S3, Cloudinary 등으로 변경
4. **썸네일 생성**: 프로필 목록 조회 시 사용
5. **배치 처리**: 정기적인 만료된 이미지 정리

