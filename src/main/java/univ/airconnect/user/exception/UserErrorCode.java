package univ.airconnect.user.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_DELETED(HttpStatus.FORBIDDEN, "USER_DELETED", "탈퇴한 사용자입니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "이용이 정지된 사용자입니다."),
    SCHOOL_EMAIL_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "SCHOOL_EMAIL_VERIFICATION_REQUIRED", "학교 이메일 인증이 필요합니다."),
    REQUIRED_CONSENT_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "REQUIRED_CONSENT_NOT_ACCEPTED", "필수 동의 항목에 모두 동의해야 합니다."),
    PASSWORD_CHANGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PASSWORD_CHANGE_NOT_ALLOWED", "비밀번호 변경은 이메일 계정에서만 가능합니다."),
    PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED", "비밀번호는 필수입니다."),
    PASSWORD_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "PASSWORD_INVALID_FORMAT", "비밀번호 형식이 올바르지 않습니다."),
    PROFILE_IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_EMPTY", "프로필 이미지 파일이 비어 있습니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_TOO_LARGE", "프로필 이미지 파일 용량이 너무 큽니다."),
    PROFILE_IMAGE_UNSUPPORTED_FORMAT(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_UNSUPPORTED_FORMAT", "지원하지 않는 프로필 이미지 형식입니다."),
    PROFILE_IMAGE_CORRUPTED(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_CORRUPTED", "프로필 이미지가 손상되었거나 올바르지 않습니다."),
    PROFILE_IMAGE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_IMAGE_STORAGE_ERROR", "프로필 이미지 저장에 실패했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.");

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
