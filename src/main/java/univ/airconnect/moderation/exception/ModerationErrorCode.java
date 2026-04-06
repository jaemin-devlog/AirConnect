package univ.airconnect.moderation.exception;

import org.springframework.http.HttpStatus;

public enum ModerationErrorCode {

    REPORT_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "MOD_REPORT_SELF_NOT_ALLOWED", "본인은 신고할 수 없습니다."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "MOD_REPORT_TARGET_NOT_FOUND", "신고 대상 사용자를 찾을 수 없습니다."),
    REPORT_DUPLICATE(HttpStatus.CONFLICT, "MOD_REPORT_DUPLICATE", "동일한 신고가 이미 접수되었습니다."),
    REPORTER_NOT_FOUND(HttpStatus.NOT_FOUND, "MOD_REPORTER_NOT_FOUND", "신고자 사용자를 찾을 수 없습니다."),

    BLOCK_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "MOD_BLOCK_SELF_NOT_ALLOWED", "본인은 차단할 수 없습니다."),
    BLOCK_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "MOD_BLOCK_TARGET_NOT_FOUND", "차단 대상 사용자를 찾을 수 없습니다."),
    BLOCKER_NOT_FOUND(HttpStatus.NOT_FOUND, "MOD_BLOCKER_NOT_FOUND", "차단 사용자 정보를 찾을 수 없습니다."),

    BLOCKED_INTERACTION(HttpStatus.FORBIDDEN, "MOD_BLOCKED_INTERACTION", "차단 관계에서는 해당 기능을 사용할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ModerationErrorCode(HttpStatus httpStatus, String code, String message) {
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
