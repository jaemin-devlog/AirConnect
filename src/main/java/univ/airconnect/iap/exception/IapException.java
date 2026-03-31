package univ.airconnect.iap.exception;

public class IapException extends RuntimeException {

    private final IapErrorCode errorCode;

    public IapException(IapErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public IapException(IapErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public IapErrorCode getErrorCode() {
        return errorCode;
    }
}

