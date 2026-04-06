package univ.airconnect.moderation.exception;

public class ModerationException extends RuntimeException {

    private final ModerationErrorCode errorCode;

    public ModerationException(ModerationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ModerationException(ModerationErrorCode errorCode, String overrideMessage) {
        super(overrideMessage);
        this.errorCode = errorCode;
    }

    public ModerationErrorCode getErrorCode() {
        return errorCode;
    }
}
