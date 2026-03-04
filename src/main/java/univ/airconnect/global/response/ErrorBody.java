package univ.airconnect.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 실패 응답 error 필드:
 * {
 *   "code": "...",
 *   "message": "...",
 *   "httpStatus": 403,
 *   "traceId": "abc123",
 *   "details": {...} | null
 * }
 */
public record ErrorBody(
        String code,
        String message,
        int httpStatus,
        String traceId,
        Object details
) { }