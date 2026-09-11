package univ.airconnect.matching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import univ.airconnect.matching.domain.entity.MatchingNotificationEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchingNotificationEventRepository extends JpaRepository<MatchingNotificationEvent, Long> {
    List<MatchingNotificationEvent> findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from MatchingNotificationEvent e where e.id = :id")
    Optional<MatchingNotificationEvent> findByIdForUpdate(@Param("id") Long id);

    long deleteByUserIdOrActorUserId(Long userId, Long actorUserId);
}
