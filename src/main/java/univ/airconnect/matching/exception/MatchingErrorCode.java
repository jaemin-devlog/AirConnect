package univ.airconnect.matching.exception;

import org.springframework.http.HttpStatus;

public enum MatchingErrorCode {

    PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "PROFILE_REQUIRED", "Profile is required to use matching"),
    PROFILE_GENDER_REQUIRED(HttpStatus.BAD_REQUEST, "PROFILE_GENDER_REQUIRED", "Profile gender is required to use matching"),
    MATCHING_RESTRICTED(HttpStatus.FORBIDDEN, "MATCHING_RESTRICTED", "User is restricted from matching"),
    INSUFFICIENT_TICKETS(HttpStatus.BAD_REQUEST, "INSUFFICIENT_TICKETS", "Insufficient tickets for matching"),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "INVALID_TARGET", "Invalid target user"),
    CANDIDATE_NOT_EXPOSED(HttpStatus.BAD_REQUEST, "CANDIDATE_NOT_EXPOSED", "Target user was not exposed as a candidate"),
    BLOCKED_USER_INTERACTION(HttpStatus.FORBIDDEN, "BLOCKED_USER_INTERACTION", "Blocked users cannot interact through matching"),
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND", "Connection not found"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request"),
    ALREADY_CONNECTED(HttpStatus.BAD_REQUEST, "ALREADY_CONNECTED", "Already sent request to this user"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");

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
