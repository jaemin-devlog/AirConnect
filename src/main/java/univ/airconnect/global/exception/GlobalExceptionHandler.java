package univ.airconnect.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.exception.SocialApiException;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.global.response.ErrorResponse;
import univ.airconnect.user.exception.UserException;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException e, HttpServletRequest request) {
        String traceId = createTraceId();

        if (e.getErrorCode() != null) {
            ErrorBody errorBody = new ErrorBody(
                    e.getErrorCode().getCode(),
                    e.getErrorCode().getMessage(),
                    e.getErrorCode().getHttpStatus().value(),
                    traceId,
                    null
            );

            return ResponseEntity
                    .status(e.getErrorCode().getHttpStatus())
                    .body(ErrorResponse.of(errorBody));
        }

        ErrorBody errorBody = new ErrorBody(
                "AUTH_ERROR",
                e.getMessage(),
                401,
                traceId,
                null
        );

        return ResponseEntity
                .status(401)
                .body(ErrorResponse.of(errorBody));
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(UserException e, HttpServletRequest request) {
        String traceId = createTraceId();

        ErrorBody errorBody = new ErrorBody(
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage(),
                e.getErrorCode().getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(errorBody));
    }

    @ExceptionHandler(SocialApiException.class)
    public ResponseEntity<ErrorResponse> handleSocialApiException(SocialApiException e, HttpServletRequest request) {
        String traceId = createTraceId();

        ErrorBody errorBody = new ErrorBody(
                e.getCode(),
                e.getMessage(),
                e.getHttpStatus().value(),
                traceId,
                null
        );

        return ResponseEntity
                .status(e.getHttpStatus())
                .body(ErrorResponse.of(errorBody));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        String traceId = createTraceId();
        log.error("Unhandled exception. traceId={}", traceId, e);

        ErrorBody errorBody = new ErrorBody(
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                500,
                traceId,
                null
        );

        return ResponseEntity
                .status(500)
                .body(ErrorResponse.of(errorBody));
    }

    private String createTraceId() {
        return UUID.randomUUID().toString();
    }
}