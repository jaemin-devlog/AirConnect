package univ.airconnect.compatibility.exception;

import org.springframework.http.HttpStatus;

public enum CompatibilityErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPATIBILITY_USER_NOT_FOUND", "User not found"),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "COMPATIBILITY_INVALID_TARGET", "Invalid target user"),
    PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "COMPATIBILITY_PROFILE_REQUIRED", "Profile is required to calculate compatibility"),
    PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "COMPATIBILITY_PROFILE_INCOMPLETE", "Required profile fields are incomplete"),
    USER_INACTIVE(HttpStatus.BAD_REQUEST, "COMPATIBILITY_USER_INACTIVE", "Only active users can calculate compatibility");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CompatibilityErrorCode(HttpStatus httpStatus, String code, String message) {
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
