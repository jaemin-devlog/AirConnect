package univ.airconnect.compatibility.exception;

import org.springframework.http.HttpStatus;

public enum CompatibilityErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPATIBILITY_USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "COMPATIBILITY_INVALID_TARGET", "유효하지 않은 대상 사용자입니다."),
    PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "COMPATIBILITY_PROFILE_REQUIRED", "궁합 계산을 위해 프로필이 필요합니다."),
    PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "COMPATIBILITY_PROFILE_INCOMPLETE", "궁합 계산에 필요한 프로필 정보가 완전하지 않습니다."),
    USER_INACTIVE(HttpStatus.BAD_REQUEST, "COMPATIBILITY_USER_INACTIVE", "활성 사용자만 궁합을 계산할 수 있습니다.");

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
