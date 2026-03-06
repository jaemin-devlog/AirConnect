package univ.airconnect.auth.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    INVALID_LOGIN_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_LOGIN_REQUEST", "Invalid login request"),
    INVALID_REFRESH_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_REFRESH_REQUEST", "Invalid refresh request"),
    INVALID_LOGOUT_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_LOGOUT_REQUEST", "Invalid logout request"),

    SOCIAL_PROVIDER_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_SOCIAL_PROVIDER_REQUIRED", "Social provider is required"),
    SOCIAL_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_SOCIAL_TOKEN_REQUIRED", "Social token is required"),
    DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_DEVICE_ID_REQUIRED", "DeviceId is required"),
    REFRESH_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_REFRESH_TOKEN_REQUIRED", "Refresh token is required"),

    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED_TOKEN", "Token expired"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "Invalid token"),
    INVALID_ACCESS_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_ACCESS_TOKEN_TYPE", "Invalid access token type"),
    INVALID_REFRESH_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN_TYPE", "Invalid refresh token type"),

    NOT_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_NOT_REFRESH_TOKEN", "Not a refresh token"),
    DEVICE_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_DEVICE_MISMATCH", "Device mismatch"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_NOT_FOUND", "Refresh token not found"),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_MISMATCH", "Refresh token mismatch"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_USER_NOT_FOUND", "User not found"),
    USER_DELETED(HttpStatus.FORBIDDEN, "USER_DELETED", "User is deleted"),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "User is suspended");

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