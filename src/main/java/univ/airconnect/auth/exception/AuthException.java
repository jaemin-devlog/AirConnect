package univ.airconnect.auth.exception;

// 우리 서버의 JWT 토큰이 만료되었거나 변조되었을 때 등, 전반적인 인증 문제 발생 시 사용할 기본 예외입니다.
public class AuthException extends RuntimeException {

  private final AuthErrorCode errorCode;

  public AuthException(String message) {
    super(message);
    this.errorCode = null;
  }

  public AuthException(String message, Throwable cause) {
    super(message, cause);
    this.errorCode = null;
  }

  public AuthException(AuthErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public AuthException(AuthErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  public AuthErrorCode getErrorCode() {
    return errorCode;
  }
}