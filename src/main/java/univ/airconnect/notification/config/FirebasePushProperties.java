package univ.airconnect.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Admin SDK 기반 푸시 발송에 사용하는 외부 설정이다.
 */
@ConfigurationProperties(prefix = "notification.push.fcm")
public record FirebasePushProperties(
        boolean enabled,
        String appName,
        String projectId,
        String credentialsPath
) {
}
