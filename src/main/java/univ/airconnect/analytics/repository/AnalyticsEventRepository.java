package univ.airconnect.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {
}
