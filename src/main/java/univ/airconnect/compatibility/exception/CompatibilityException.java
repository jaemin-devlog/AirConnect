package univ.airconnect.compatibility.exception;

public class CompatibilityException extends RuntimeException {

    private final CompatibilityErrorCode errorCode;

    public CompatibilityException(CompatibilityErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CompatibilityErrorCode getErrorCode() {
        return errorCode;
    }
}
