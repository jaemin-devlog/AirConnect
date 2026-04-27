package univ.airconnect.auth.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    INVALID_LOGIN_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_LOGIN_REQUEST", "로그인 요청이 올바르지 않습니다."),
    INVALID_REFRESH_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_REFRESH_REQUEST", "토큰 재발급 요청이 올바르지 않습니다."),
    INVALID_LOGOUT_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_LOGOUT_REQUEST", "로그아웃 요청이 올바르지 않습니다."),

    SOCIAL_PROVIDER_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_SOCIAL_PROVIDER_REQUIRED", "소셜 로그인 제공자가 필요합니다."),
    SOCIAL_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_SOCIAL_TOKEN_REQUIRED", "소셜 토큰이 필요합니다."),
    KAKAO_LOGIN_DISABLED(HttpStatus.BAD_REQUEST, "AUTH_KAKAO_LOGIN_DISABLED", "카카오 로그인은 현재 지원하지 않습니다."),
    DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_DEVICE_ID_REQUIRED", "deviceId가 필요합니다."),
    REFRESH_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_REFRESH_TOKEN_REQUIRED", "Refresh Token이 필요합니다."),
    EMAIL_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_PASSWORD_REQUIRED", "비밀번호가 필요합니다."),
    EMAIL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_EMAIL_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_LOGIN_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_EMAIL_LOGIN_TEMPORARILY_LOCKED", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다."),
    EMAIL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_EMAIL_ACCOUNT_NOT_FOUND", "이메일 계정을 찾을 수 없습니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "AUTH_INVALID_PASSWORD_FORMAT", "비밀번호 형식이 올바르지 않습니다."),

    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED_TOKEN", "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    INVALID_APPLE_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_APPLE_TOKEN", "유효하지 않은 Apple 토큰입니다."),
    INVALID_ACCESS_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_ACCESS_TOKEN_TYPE", "유효하지 않은 Access Token 타입입니다."),
    INVALID_REFRESH_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN_TYPE", "유효하지 않은 Refresh Token 타입입니다."),

    NOT_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_NOT_REFRESH_TOKEN", "Refresh Token이 아닙니다."),
    DEVICE_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_DEVICE_MISMATCH", "디바이스 정보가 일치하지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_NOT_FOUND", "저장된 Refresh Token을 찾을 수 없습니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_MISMATCH", "Refresh Token이 일치하지 않습니다."),
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_REUSE_DETECTED", "Refresh Token 재사용이 감지되었습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_DELETED(HttpStatus.FORBIDDEN, "USER_DELETED", "삭제된 사용자입니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "정지된 사용자입니다."),
    USER_RESTRICTED(HttpStatus.FORBIDDEN, "USER_RESTRICTED", "제한된 사용자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
