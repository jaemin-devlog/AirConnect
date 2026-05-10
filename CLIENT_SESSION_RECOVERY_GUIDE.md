# AirConnect 클라이언트 세션 복구 가이드

## 목적

백그라운드 복귀 후 access token 만료나 연결 단절이 발생해도 사용자를 바로 앱 시작 화면으로 보내지 않고, 가능한 경우 조용히 세션을 복구한다.

핵심 원칙은 아래와 같다.

- `401 -> 바로 시작 화면 이동`으로 처리하지 않는다.
- `401 -> refresh 1회 시도 -> 성공 시 원요청 재시도`로 처리한다.
- refresh 실패일 때만 세션을 정리하고 시작 화면 또는 로그인 화면으로 보낸다.
- 웹소켓/STOMP 연결은 refresh 성공 후 새 access token으로 다시 연결한다.

## 서버 전제

서버는 이제 보호된 REST API에서 JSON 형태의 `401/403` 응답과 `error.code`를 내려준다.

- `AUTH_EXPIRED_TOKEN`
- `AUTH_INVALID_TOKEN`
- `AUTH_INVALID_ACCESS_TOKEN_TYPE`
- `AUTH_REFRESH_TOKEN_NOT_FOUND`
- `AUTH_REFRESH_TOKEN_REUSE_DETECTED`
- `AUTH_DEVICE_MISMATCH`
- `AUTH-001`
- `AUTH-002`
- `USER_DELETED`
- `USER_SUSPENDED`
- `USER_RESTRICTED`

클라이언트는 HTTP status만 보지 말고 반드시 `error.code` 기준으로 분기해야 한다.

## 필수 구현 항목

- 인증 상태를 `AuthStore` 같은 단일 소스에서 관리한다.
- `accessToken`, `refreshToken`, `deviceId`, 로그인 상태를 메모리와 영속 저장소에서 일관되게 관리한다.
- 로그인 시 사용한 `deviceId`를 refresh와 logout에도 동일하게 사용한다.
- 보호된 API는 공통 인터셉터 또는 네트워크 레이어 한 곳에서 `401`을 처리한다.
- `/auth/refresh` 요청 자체는 refresh 재시도 대상에서 제외한다.
- 여러 요청이 동시에 `401`을 맞아도 refresh는 한 번만 실행되도록 `single-flight` 처리한다.
- refresh 진행 중 새로 실패한 요청은 큐에 넣고 refresh 결과를 기다린다.
- refresh 성공 시 대기 중인 요청은 새 access token으로 한 번만 재시도한다.
- 무한 루프를 막기 위해 요청별 `retryOnce` 플래그를 둔다.
- refresh 성공 시 메모리 토큰, 영속 저장소 토큰, 공통 Authorization 헤더 소스를 즉시 새 값으로 교체한다.
- background -> foreground 복귀 시 `ensureSession()` 같은 세션 복구 루틴을 실행한다.
- 네트워크 오프라인과 인증 만료를 구분한다. 오프라인인 경우 로그아웃시키지 않는다.
- refresh 실패 시 토큰, 사용자 세션, 인증 캐시, 소켓 연결, 화면 스택을 모두 정리한다.
- 앱을 실제 종료하지 말고, 세션과 라우팅 상태만 초기화한다.

## 401 처리 규칙

### refresh 시도 대상

아래 코드는 refresh를 1회 시도한다.

- `AUTH_EXPIRED_TOKEN`
- `AUTH_INVALID_TOKEN`
- `AUTH_INVALID_ACCESS_TOKEN_TYPE`
- `AUTH-001`

`AUTH-001`은 토큰이 없는 경우일 수도 있으므로 로컬에 토큰이 있을 때만 refresh를 시도한다. 로컬에 토큰이 없으면 바로 시작 화면 또는 로그인 화면으로 보낸다.

### 즉시 세션 종료 대상

아래 코드는 refresh로 복구하지 말고 세션 정리 후 시작 화면 또는 로그인 화면으로 보낸다.

- `AUTH_REFRESH_TOKEN_NOT_FOUND`
- `AUTH_REFRESH_TOKEN_REUSE_DETECTED`
- `AUTH_DEVICE_MISMATCH`

### 별도 안내 대상

아래 코드는 단순 로그인 만료가 아니므로 별도 안내 문구가 필요하다.

- `USER_DELETED`
- `USER_SUSPENDED`
- `USER_RESTRICTED`

### refresh 대상 아님

- `AUTH-002`

이 경우는 권한 부족이므로 refresh를 시도하지 않고 현재 화면에서 에러 처리한다.

## 권장 상태 흐름

```text
보호된 API 호출
-> 401 수신
-> error.code 확인
-> refresh 대상인지 판별
-> refresh 1회 시도
-> 성공: 토큰 저장 + 원요청 재시도 + 소켓 재연결
-> 실패: 세션 정리 + 시작/로그인 화면 이동
```

## 의사코드 예시

```ts
async function handleUnauthorized(request, response) {
  const code = response.error?.code;

  if (!shouldTryRefresh(code, authStore.hasRefreshToken())) {
    await hardLogout(code);
    return Promise.reject(response);
  }

  if (request.retryOnce) {
    await hardLogout(code);
    return Promise.reject(response);
  }

  const refreshResult = await authCoordinator.refreshOnce();

  if (!refreshResult.ok) {
    await hardLogout(refreshResult.code ?? code);
    return Promise.reject(response);
  }

  request.retryOnce = true;
  request.headers.Authorization = `Bearer ${refreshResult.accessToken}`;
  return httpClient.request(request);
}
```

## foreground 복귀 처리

- 앱이 foreground로 돌아오면 `ensureSession()`을 호출한다.
- access token 만료가 임박했거나 이미 만료된 경우 refresh를 먼저 시도한다.
- refresh 성공 후 필요한 초기 API를 다시 호출한다.
- 채팅/매칭 기능이 열려 있으면 소켓 연결을 재수립한다.
- 복구 중에는 짧은 로딩 오버레이를 사용하고, 사용자가 매번 시작 화면을 보지 않게 한다.

## 웹소켓/STOMP 처리

- refresh 성공 후 기존 소켓은 끊고 새 access token으로 다시 `CONNECT` 한다.
- 재연결 후 필요한 subscribe를 다시 수행한다.
- 채팅방 구독
- 채팅 목록 구독
- 매칭 관련 구독
- 오래된 access token으로 열린 소켓을 계속 재사용하지 않는다.
- 소켓 인증 실패가 access token 만료 때문이면 refresh 후 재연결을 1회 시도한다.

## 세션 종료 처리

refresh 실패 또는 복구 불가 상태일 때는 아래를 한 번에 수행한다.

- `accessToken` 삭제
- `refreshToken` 삭제
- 메모리 인증 상태 초기화
- 사용자 캐시 초기화
- 웹소켓 연결 종료
- 인증이 필요한 화면 스택 정리
- 시작 화면 또는 로그인 화면으로 이동

중복 로그아웃, 중복 화면 이동, 중복 알림이 발생하지 않도록 `logoutInProgress` 같은 보호 장치를 둔다.

## UX 원칙

- 사용자는 단순 토큰 만료 때문에 시작 화면으로 튕겨서는 안 된다.
- 복구 가능한 경우 현재 보던 화면을 유지해야 한다.
- deep link나 push로 진입한 경우 복구 성공 후 원래 목적 화면으로 복원하는 것이 좋다.
- 시작 화면은 "401이 뜰 때마다 무조건 가는 화면"이 아니라 "복구 실패 후 진입하는 허브"로 사용한다.

## 로그 및 관측

아래 이벤트를 남기면 장애 추적이 쉬워진다.

- `401_received`
- `refresh_started`
- `refresh_succeeded`
- `refresh_failed`
- `socket_reconnect_started`
- `socket_reconnect_succeeded`
- `session_hard_logout`

서버가 내려주는 `X-Trace-Id`를 함께 보관하면 서버 로그와 대조하기 쉽다.

## QA 체크리스트

- 백그라운드에서 오래 있다가 복귀해도 바로 시작 화면으로 이동하지 않고 자동 복구되는지
- 동시에 여러 API가 `401`을 맞아도 refresh가 한 번만 호출되는지
- refresh 성공 시 원요청이 정상 재시도되는지
- refresh 실패 시 세션이 한 번만 정리되고 중복 이동하지 않는지
- refresh 후 채팅/매칭 소켓이 자동 재연결되는지
- 네트워크 오프라인일 때 로그아웃되지 않는지
- 앱 재실행 후 refresh token만 살아 있어도 세션 복구가 가능한지
- `USER_SUSPENDED`, `USER_RESTRICTED`, `USER_DELETED`가 일반 로그인 만료와 다르게 안내되는지

## 한 줄 정리

클라이언트는 `401 -> 시작 화면`으로 처리하지 말고, `401 -> refresh 1회 -> 성공 시 복구, 실패 시 세션 종료`로 구현해야 한다.
