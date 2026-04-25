package univ.airconnect.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

@Slf4j
@Component
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "notification.push.fcm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushNotificationSender implements PushNotificationSender {

    @Override
    public PushSendResult send(NotificationOutbox outbox) {
        log.info("Push dispatch simulated: outboxId={}, userId={}, provider={}",
                outbox.getId(), outbox.getUserId(), outbox.getProvider());
        return PushSendResult.success("simulated-" + outbox.getId());
    }
}
