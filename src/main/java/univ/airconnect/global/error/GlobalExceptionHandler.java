package univ.airconnect.global.error;

import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.exception.UserErrorCode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
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
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
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
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode ec = ErrorCode.INVALID_REQUEST;

        Map<String, Object> details = new HashMap<>();
        details.put("name", e.getName());
        details.put("value", e.getValue());
        details.put("requiredType", e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : null);

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
