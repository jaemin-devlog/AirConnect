package univ.airconnect.verification.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum VerificationErrorCode {

    INVALID_EMAIL_DOMAIN(HttpStatus.BAD_REQUEST, "VERIFY_INVALID_EMAIL_DOMAIN", "지원하지 않는 이메일 도메인입니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "VERIFY_INVALID_EMAIL_FORMAT", "올바른 이메일 형식이 아닙니다."),
    CODE_EXPIRED(HttpStatus.BAD_REQUEST, "VERIFY_CODE_EXPIRED", "인증 코드가 만료되었거나 존재하지 않습니다."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "VERIFY_CODE_MISMATCH", "인증 코드가 일치하지 않습니다."),
    VERIFIED_EMAIL_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "VERIFY_VERIFIED_EMAIL_TOKEN_REQUIRED", "인증 완료 토큰이 필요합니다."),
    VERIFIED_EMAIL_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "VERIFY_VERIFIED_EMAIL_TOKEN_EXPIRED", "인증 완료 토큰이 만료되었거나 유효하지 않습니다."),
    VERIFIED_EMAIL_MISMATCH(HttpStatus.BAD_REQUEST, "VERIFY_VERIFIED_EMAIL_MISMATCH", "인증된 이메일과 요청 정보가 일치하지 않습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_TOO_MANY_REQUESTS", "인증 요청이 너무 잦습니다. 잠시 후 다시 시도해주세요."),
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VERIFY_MAIL_SEND_FAILED", "메일 발송에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    VerificationErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
