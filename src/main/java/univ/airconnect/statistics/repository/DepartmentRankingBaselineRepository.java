package univ.airconnect.statistics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.statistics.domain.entity.DepartmentRankingBaseline;

public interface DepartmentRankingBaselineRepository
        extends JpaRepository<DepartmentRankingBaseline, Long> {
}
