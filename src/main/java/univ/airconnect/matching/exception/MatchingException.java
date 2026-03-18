package univ.airconnect.matching.exception;

public class MatchingException extends RuntimeException {

    private final MatchingErrorCode errorCode;

    public MatchingException(MatchingErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public MatchingErrorCode getErrorCode() {
        return errorCode;
    }
}

