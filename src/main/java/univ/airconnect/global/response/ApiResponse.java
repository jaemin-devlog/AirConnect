package univ.airconnect.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 공통 응답 포맷:
 * 성공:
 * {
 *   "success": true,
 *   "data": ...,
 *   "traceId": "..."
 * }
 *
 * 실패:
 * {
 *   "success": false,
 *   "data": null,
 *   "error": {
 *     "code": "...",
 *     "message": "...",
 *     "httpStatus": 403,
 *     "traceId": "abc123",
 *     "details": {...} | null
 *   },
 *   "traceId": "abc123"
 * }
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorBody error;
    private final String traceId;

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, traceId);
    }

    public static ApiResponse<Void> ok(String traceId) {
        return new ApiResponse<>(true, null, null, traceId);
    }

    public static ApiResponse<Void> fail(ErrorBody error, String traceId) {
        return new ApiResponse<>(false, null, error, traceId);
    }
}