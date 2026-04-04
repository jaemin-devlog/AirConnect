package univ.airconnect.ads.exception;

import org.springframework.http.HttpStatus;

public enum AdsErrorCode {

    AD_REWARD_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AD_REWARD_DAILY_LIMIT_EXCEEDED", "일일 광고 보상 횟수를 초과했습니다."),
    AD_REWARD_INVALID_SESSION(HttpStatus.BAD_REQUEST, "AD_REWARD_INVALID_SESSION", "유효하지 않은 광고 보상 세션입니다."),
    AD_REWARD_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "AD_REWARD_SESSION_EXPIRED", "광고 보상 세션이 만료되었습니다."),
    AD_REWARD_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "AD_REWARD_INVALID_SIGNATURE", "광고 콜백 서명 검증에 실패했습니다."),
    AD_REWARD_DUPLICATE_TRANSACTION(HttpStatus.CONFLICT, "AD_REWARD_DUPLICATE_TRANSACTION", "이미 처리된 광고 거래입니다."),
    AD_REWARD_DUPLICATE_REQUEST(HttpStatus.CONFLICT, "AD_REWARD_DUPLICATE_REQUEST", "중복 광고 보상 요청입니다."),
    AD_REWARD_INVALID_CALLBACK(HttpStatus.BAD_REQUEST, "AD_REWARD_INVALID_CALLBACK", "광고 콜백 파라미터가 유효하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AdsErrorCode(HttpStatus httpStatus, String code, String message) {
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

