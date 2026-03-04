package univ.airconnect.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 성공 응답 포맷:
 * {
 *   "success": true,
 *   "data": ...
 * }
 */

public record ApiResponse<T>(
        boolean success,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }
}