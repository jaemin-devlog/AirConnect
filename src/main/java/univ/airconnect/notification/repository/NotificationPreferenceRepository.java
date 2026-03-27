package univ.airconnect.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.notification.domain.entity.NotificationPreference;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    java.util.Optional<NotificationPreference> findByUserId(Long userId);
}
