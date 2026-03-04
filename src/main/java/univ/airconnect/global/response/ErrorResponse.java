package univ.airconnect.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 실패 응답 포맷:
 * {
 *   "success": false,
 *   "error": { ... }
 * }
 */
public record ErrorResponse(
        boolean success,
        ErrorBody error
) {
    public static ErrorResponse of(ErrorBody errorBody) {
        return new ErrorResponse(false, errorBody);
    }
}