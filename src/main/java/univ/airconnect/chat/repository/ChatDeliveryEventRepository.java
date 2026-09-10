package univ.airconnect.chat.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import univ.airconnect.chat.domain.entity.ChatDeliveryEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatDeliveryEventRepository extends JpaRepository<ChatDeliveryEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ChatDeliveryEvent e where e.id = :id")
    Optional<ChatDeliveryEvent> findByIdForUpdate(@Param("id") Long id);

    boolean existsByLaneAndIdLessThan(String lane, Long id);

    @Query("select e.id from ChatDeliveryEvent e where e.nextAttemptAt <= :now " +
            "and not exists (select older.id from ChatDeliveryEvent older where older.lane = e.lane and older.id < e.id) " +
            "order by e.nextAttemptAt, e.id")
    List<Long> findDueHeads(@Param("now") LocalDateTime now, Pageable pageable);
}
