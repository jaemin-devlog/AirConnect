package univ.airconnect.ads.exception;

public class AdsException extends RuntimeException {

    private final AdsErrorCode errorCode;

    public AdsException(AdsErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AdsException(AdsErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AdsErrorCode getErrorCode() {
        return errorCode;
    }
}

