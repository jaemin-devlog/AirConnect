package univ.airconnect.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

/**
 * 실제 FCM 발송이 꺼져 있을 때 사용하는 기본 발송기이다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification.push.fcm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushNotificationSender implements PushNotificationSender {

    /**
     * 나머지 outbox 파이프라인을 로컬에서 검증할 수 있도록 성공 발송을 흉내 낸다.
     */
    @Override
    public PushSendResult send(NotificationOutbox outbox) {
        log.info("Push dispatch simulated: outboxId={}, userId={}, provider={}",
                outbox.getId(), outbox.getUserId(), outbox.getProvider());
        return PushSendResult.success("simulated-" + outbox.getId());
    }
}
