package univ.airconnect.iap.exception;

import org.springframework.http.HttpStatus;

public enum IapErrorCode {

    IAP_INVALID_PRODUCT(HttpStatus.BAD_REQUEST, "IAP_INVALID_PRODUCT", "유효하지 않은 상품입니다."),
    IAP_INVALID_TRANSACTION(HttpStatus.BAD_REQUEST, "IAP_INVALID_TRANSACTION", "유효하지 않은 거래입니다."),
    IAP_STORE_VERIFY_FAILED(HttpStatus.BAD_GATEWAY, "IAP_STORE_VERIFY_FAILED", "스토어 검증에 실패했습니다."),
    IAP_ENVIRONMENT_MISMATCH(HttpStatus.BAD_REQUEST, "IAP_ENVIRONMENT_MISMATCH", "결제 환경이 일치하지 않습니다."),
    IAP_DUPLICATE_REQUEST(HttpStatus.CONFLICT, "IAP_DUPLICATE_REQUEST", "중복 결제 검증 요청입니다."),
    IAP_FORBIDDEN(HttpStatus.FORBIDDEN, "IAP_FORBIDDEN", "거래 조회 권한이 없습니다."),
    IAP_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "IAP_UNAUTHORIZED", "인증이 필요합니다."),
    IAP_APPLE_VERIFY_FAILED(HttpStatus.BAD_GATEWAY, "IAP_APPLE_VERIFY_FAILED", "애플 결제 검증에 실패했습니다."),
    IAP_GOOGLE_VERIFY_FAILED(HttpStatus.BAD_GATEWAY, "IAP_GOOGLE_VERIFY_FAILED", "구글 결제 검증에 실패했습니다."),
    IAP_ACCOUNT_TOKEN_MISMATCH(HttpStatus.BAD_REQUEST, "IAP_ACCOUNT_TOKEN_MISMATCH", "appAccountToken 검증에 실패했습니다."),
    IAP_ALREADY_PROCESSED(HttpStatus.OK, "IAP_ALREADY_PROCESSED", "이미 처리된 거래입니다."),
    IAP_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "IAP_ORDER_NOT_FOUND", "결제 거래를 찾을 수 없습니다."),
    IAP_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "IAP_PROVIDER_NOT_SUPPORTED", "지원하지 않는 결제 스토어입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    IapErrorCode(HttpStatus httpStatus, String code, String message) {
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

