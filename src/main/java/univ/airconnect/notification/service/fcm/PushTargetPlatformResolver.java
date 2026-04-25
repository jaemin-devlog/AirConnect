package univ.airconnect.notification.service.fcm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushTargetPlatformResolver {

    private final PushDeviceRepository pushDeviceRepository;

    public PushPlatform resolve(NotificationOutbox outbox) {
        return pushDeviceRepository.findById(outbox.getPushDeviceId())
                .map(PushDevice::getPlatform)
                .orElseGet(() -> {
                    log.warn("Push device not found while building FCM message. Falling back to generic payload: outboxId={}, pushDeviceId={}",
                            outbox.getId(), outbox.getPushDeviceId());
                    return null;
                });
    }
}
