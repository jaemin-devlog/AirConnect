package univ.airconnect.notification.service;

import univ.airconnect.notification.domain.entity.NotificationOutbox;

/**
 * 외부 푸시 프로바이더 발송을 추상화한 인터페이스다.
 *
 * <p>현재는 로깅 기반 기본 구현을 사용하고, 이후 Firebase Admin SDK 기반 구현으로 교체한다.</p>
 */
public interface PushNotificationSender {

    /**
     * 단건 outbox를 외부 프로바이더로 발송한다.
     */
    PushSendResult send(NotificationOutbox outbox);

    /**
     * 발송 결과를 워커가 공통 처리할 수 있도록 정규화한 결과 모델이다.
     */
    record PushSendResult(
            boolean success,
            boolean retryable,
            boolean invalidToken,
            String providerMessageId,
            String errorCode,
            String errorMessage
    ) {
        public static PushSendResult success(String providerMessageId) {
            return new PushSendResult(true, false, false, providerMessageId, null, null);
        }

        public static PushSendResult retryableFailure(String errorCode, String errorMessage) {
            return new PushSendResult(false, true, false, null, errorCode, errorMessage);
        }

        public static PushSendResult invalidToken(String errorCode, String errorMessage) {
            return new PushSendResult(false, false, true, null, errorCode, errorMessage);
        }

        public static PushSendResult failed(String errorCode, String errorMessage) {
            return new PushSendResult(false, false, false, null, errorCode, errorMessage);
        }
    }
}
