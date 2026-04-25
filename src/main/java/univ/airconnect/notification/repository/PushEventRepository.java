package univ.airconnect.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.notification.domain.PushEventType;
import univ.airconnect.notification.domain.entity.PushEvent;

import java.util.Optional;

public interface PushEventRepository extends JpaRepository<PushEvent, Long> {

    Optional<PushEvent> findTopByUserIdAndNotificationIdAndEventTypeAndDeviceIdOrderByIdDesc(
            Long userId,
            Long notificationId,
            PushEventType eventType,
            String deviceId
    );
}
