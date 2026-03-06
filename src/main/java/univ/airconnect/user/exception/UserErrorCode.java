package univ.airconnect.user.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"),
    USER_DELETED(HttpStatus.FORBIDDEN, "USER_DELETED", "User is deleted"),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "User is suspended");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus httpStatus, String code, String message) {
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