package univ.airconnect.global.error;

import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MatchingException.class)
    public ResponseEntity<ApiResponse<Void>> handleMatching(MatchingException e, HttpServletRequest request) {
        MatchingErrorCode mec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("❌ MatchingException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                mec.getCode(),
                e.getMessage(),
                mec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(mec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerification(VerificationException e, HttpServletRequest request) {
        VerificationErrorCode vec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("❌ VerificationException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                vec.getCode(),
                e.getMessage(),
                vec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(vec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode ec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("❌ BusinessException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                e.getMessage(),                 // overrideMessage면 override 반영
                ec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                                HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        String message = e.getName() + " 타입이 올바르지 않습니다.";

        log.warn("❌ MethodArgumentTypeMismatchException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                message,
                ec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e,
                                                                        HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        log.warn("❌ HttpMediaTypeNotSupportedException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                "지원하지 않는 Content-Type 입니다. multipart/form-data 형식으로 요청해주세요.",
                e.getStatusCode().value(),
                traceId,
                null
        );

        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e,
                                                             HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        log.warn("❌ MultipartException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                "multipart 요청 형식이 올바르지 않습니다.",
                ec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.error("🔴 Unexpected Exception [{}]", traceId, e);

        ErrorCode ec = ErrorCode.INTERNAL_ERROR;

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                ec.getMessage(),
                ec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }
}