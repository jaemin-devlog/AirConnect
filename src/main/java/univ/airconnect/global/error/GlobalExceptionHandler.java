package univ.airconnect.global.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import univ.airconnect.ads.exception.AdsErrorCode;
import univ.airconnect.ads.exception.AdsException;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        List<Map<String, String>> details = e.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorToDetail)
                .toList();

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                ec.getMessage(),
                ec.getHttpStatus().value(),
                traceId,
                details
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException e,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        List<Map<String, String>> details = e.getConstraintViolations().stream()
                .map(this::constraintViolationToDetail)
                .toList();

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                ec.getMessage(),
                ec.getHttpStatus().value(),
                traceId,
                details
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        Map<String, Object> details = new HashMap<>();
        details.put("name", e.getName());
        details.put("value", e.getValue());
        details.put(
                "requiredType",
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : null
        );

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                ec.getMessage(),
                ec.getHttpStatus().value(),
                traceId,
                details
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException e, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        AuthErrorCode aec = e.getErrorCode();

        if (aec == null) {
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

        ErrorBody body = new ErrorBody(
                aec.getCode(),
                e.getMessage(),
                aec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(aec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiResponse<Void>> handleUser(UserException e, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        UserErrorCode uec = e.getErrorCode();

        ErrorBody body = new ErrorBody(
                uec.getCode(),
                e.getMessage(),
                uec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(uec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(MatchingException.class)
    public ResponseEntity<ApiResponse<Void>> handleMatching(
            MatchingException e,
            HttpServletRequest request
    ) {
        MatchingErrorCode mec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("MatchingException [{}] - {}", traceId, e.getMessage());

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
    public ResponseEntity<ApiResponse<Void>> handleVerification(
            VerificationException e,
            HttpServletRequest request
    ) {
        VerificationErrorCode vec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("VerificationException [{}] - {}", traceId, e.getMessage());

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

    @ExceptionHandler(IapException.class)
    public ResponseEntity<ApiResponse<Void>> handleIap(
            IapException e,
            HttpServletRequest request
    ) {
        IapErrorCode iec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("IapException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                iec.getCode(),
                e.getMessage(),
                iec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(iec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(AdsException.class)
    public ResponseEntity<ApiResponse<Void>> handleAds(
            AdsException e,
            HttpServletRequest request
    ) {
        AdsErrorCode aec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("AdsException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                aec.getCode(),
                e.getMessage(),
                aec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(aec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException e,
            HttpServletRequest request
    ) {
        ErrorCode ec = e.getErrorCode();
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);

        log.warn("BusinessException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                e.getMessage(),
                ec.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        log.warn("HttpMediaTypeNotSupportedException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                "Unsupported Content-Type. Please use multipart/form-data.",
                e.getStatusCode().value(),
                traceId,
                null
        );

        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.fail(body, traceId));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(
            MultipartException e,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        log.warn("MultipartException [{}] - {}", traceId, e.getMessage());

        ErrorBody body = new ErrorBody(
                ec.getCode(),
                "Invalid multipart request format.",
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

        log.error("Unexpected exception [{}]", traceId, e);

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

    private Map<String, String> fieldErrorToDetail(FieldError error) {
        Map<String, String> detail = new HashMap<>();
        detail.put("field", error.getField());
        detail.put("message", error.getDefaultMessage());
        return detail;
    }

    private Map<String, String> constraintViolationToDetail(ConstraintViolation<?> violation) {
        Map<String, String> detail = new HashMap<>();
        detail.put("path", String.valueOf(violation.getPropertyPath()));
        detail.put("message", violation.getMessage());
        return detail;
    }
}
