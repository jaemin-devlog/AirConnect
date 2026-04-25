package univ.airconnect.notification.service;

import java.time.LocalDateTime;

public record GuardResult(
        DeliveryDecision decision,
        LocalDateTime nextAttemptAt,
        String reason,
        String message
) {

    public static GuardResult sendNow() {
        return new GuardResult(DeliveryDecision.SEND_NOW, null, null, null);
    }

    public static GuardResult defer(LocalDateTime nextAttemptAt, String reason, String message) {
        return new GuardResult(DeliveryDecision.DEFER, nextAttemptAt, reason, message);
    }

    public static GuardResult skip(String reason, String message) {
        return new GuardResult(DeliveryDecision.SKIP, null, reason, message);
    }

    public static GuardResult fail(String reason, String message) {
        return new GuardResult(DeliveryDecision.FAIL, null, reason, message);
    }
}
