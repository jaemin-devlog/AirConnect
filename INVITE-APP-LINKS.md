# 그룹 매칭 초대 앱 링크 배포 설정

공유 URL은 `https://airconnect.cloud/join#123456` 형식을 사용한다. 초대 코드는 URL fragment에 있으므로 HTTP 요청, 프록시 접근 로그, referrer에 포함되지 않는다.

## 운영 환경 필수 설정

1. 기존 `airconnect.cloud` DNS와 Caddy 배포가 정상 동작하는지 확인한다. 별도 초대 서브도메인은 만들지 않는다.
2. Play Console의 **앱 서명 키 인증서** SHA-256 지문을 서버 `.env`에 등록한다. 업로드 키가 아니라 Play App Signing 지문이어야 한다. 지문이 여러 개면 쉼표로 구분한다.

   ```text
   APP_LINK_ANDROID_SHA256_CERT_FINGERPRINTS=AA:BB:...,CC:DD:...
   ```

3. iOS 운영 환경의 기존 `TEAM_ID`와 `BUNDLE_ID`가 실제 배포 앱 값인지 확인한다. 필요하면 Spring 실행 환경에 아래 값을 직접 지정할 수 있다.

   ```text
   APP_LINK_IOS_TEAM_ID=실제_Apple_Team_ID
   APP_LINK_IOS_BUNDLE_ID=실제_iOS_Bundle_ID
   ```

4. iOS 앱 Target의 Associated Domains에 다음 값을 추가하고, 수신한 Universal Link의 fragment에서 6자리 코드를 읽어 그룹 매칭 참여 코드 입력 화면으로 전달한다.

   ```text
   applinks:airconnect.cloud
   ```

## 배포 후 확인 주소

- 초대 안내: `https://airconnect.cloud/join#578183`
- Android 연결 파일: `https://airconnect.cloud/.well-known/assetlinks.json`
- iOS 연결 파일: `https://airconnect.cloud/.well-known/apple-app-site-association`

연결 파일은 리다이렉트 없이 HTTPS 200과 `application/json`으로 응답해야 한다. 필수 서명 값이 설정되지 않으면 서버는 잘못된 연결 파일을 배포하는 대신 503을 반환한다.

초대 링크에 노출되는 값은 공식 서비스 도메인과 6자리 코드뿐이다. 코드가 fragment에 있으므로 서버의 `/join` 요청과 접근 로그에는 코드가 전달되지 않는다.
