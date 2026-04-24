package univ.airconnect.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;

import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    List<AnalyticsEvent> findTop20ByUserIdOrderByOccurredAtDescIdDesc(Long userId);
}
