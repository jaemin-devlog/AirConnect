package univ.airconnect.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * 외부 소셜 서버(카카오, 애플 등)와 통신하는 과정에서 발생하는 모든 에러를 처리하기 위한 커스텀 예외 클래스
 */
public class SocialApiException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public SocialApiException(String message) {
        super(message);
        this.code = "SOCIAL_API_ERROR";
        this.httpStatus = HttpStatus.BAD_GATEWAY;
    }

    public SocialApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = "SOCIAL_API_ERROR";
        this.httpStatus = HttpStatus.BAD_GATEWAY;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}