package univ.airconnect.matching.exception;

import org.springframework.http.HttpStatus;

public enum MatchingErrorCode {

    PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "PROFILE_REQUIRED", "매칭을 이용하려면 프로필이 필요합니다."),
    PROFILE_GENDER_REQUIRED(HttpStatus.BAD_REQUEST, "PROFILE_GENDER_REQUIRED", "매칭을 이용하려면 프로필 성별 정보가 필요합니다."),
    INSUFFICIENT_TICKETS(HttpStatus.BAD_REQUEST, "INSUFFICIENT_TICKETS", "매칭에 필요한 티켓이 부족합니다."),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "INVALID_TARGET", "유효하지 않은 대상 사용자입니다."),
    CANDIDATE_NOT_EXPOSED(HttpStatus.BAD_REQUEST, "CANDIDATE_NOT_EXPOSED", "대상 사용자가 추천 후보로 노출되지 않았습니다."),
    BLOCKED_USER_INTERACTION(HttpStatus.FORBIDDEN, "BLOCKED_USER_INTERACTION", "차단된 사용자와는 매칭을 통해 상호작용할 수 없습니다."),
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND", "연결 정보를 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    ALREADY_CONNECTED(HttpStatus.BAD_REQUEST, "ALREADY_CONNECTED", "이미 이 사용자에게 요청을 보냈습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    MatchingErrorCode(HttpStatus httpStatus, String code, String message) {
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
