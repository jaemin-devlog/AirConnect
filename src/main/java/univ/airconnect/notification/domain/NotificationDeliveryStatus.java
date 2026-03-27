package univ.airconnect.notification.domain;

/**
 * 푸시 발송 작업(notification_outbox)의 현재 처리 상태다.
 */
public enum NotificationDeliveryStatus {
    /** 아직 발송 전이거나 재시도 대기 중인 상태 */
    PENDING,
    /** 워커가 현재 점유하여 발송을 진행 중인 상태 */
    PROCESSING,
    /** 외부 푸시 프로바이더까지 정상 발송된 상태 */
    SENT,
    /** 더 이상 재시도하지 않는 실패 상태 */
    FAILED,
    /** 정책상 발송하지 않기로 결정된 상태 */
    SKIPPED
}
