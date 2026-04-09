package univ.airconnect.user.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"),
    USER_DELETED(HttpStatus.FORBIDDEN, "USER_DELETED", "User is deleted"),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "User is suspended"),
    REQUIRED_CONSENT_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "REQUIRED_CONSENT_NOT_ACCEPTED", "필수 동의 항목에 모두 동의해야 합니다."),
    PASSWORD_CHANGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PASSWORD_CHANGE_NOT_ALLOWED", "Password change is only available for email accounts"),
    PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED", "Password is required"),
    PASSWORD_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "PASSWORD_INVALID_FORMAT", "Password format is invalid"),
    PROFILE_IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_EMPTY", "Profile image file is empty"),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_TOO_LARGE", "Profile image file is too large"),
    PROFILE_IMAGE_UNSUPPORTED_FORMAT(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_UNSUPPORTED_FORMAT", "Unsupported profile image format"),
    PROFILE_IMAGE_CORRUPTED(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_CORRUPTED", "Corrupted or invalid profile image"),
    PROFILE_IMAGE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_IMAGE_STORAGE_ERROR", "Failed to store profile image"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid input provided");

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
