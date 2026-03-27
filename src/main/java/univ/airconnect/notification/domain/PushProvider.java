package univ.airconnect.notification.domain;

/**
 * 서버가 실제 발송에 사용하는 푸시 프로바이더다.
 * 1차 구현은 FCM 중심으로 사용하고, 필요 시 APNS direct 발송으로 확장한다.
 */
public enum PushProvider {
    /** Firebase Cloud Messaging 프로바이더 */
    FCM,
    /** Apple Push Notification Service 프로바이더 */
    APNS
}
