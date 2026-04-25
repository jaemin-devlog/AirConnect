package univ.airconnect.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.notification.domain.entity.PushEvent;

public interface PushEventRepository extends JpaRepository<PushEvent, Long> {
}
