package univ.airconnect.analytics.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.analytics.domain.entity.ApiRequestLog;

import java.time.LocalDateTime;
import java.util.List;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    @Query("""
        SELECT COUNT(DISTINCT CONCAT(CONCAT(l.method, ' '), l.path))
        FROM ApiRequestLog l
        WHERE l.createdAt >= :since
    """)
    long countDistinctEndpointsSince(@Param("since") LocalDateTime since);

    @Query("""
        SELECT l.method AS method,
               l.path AS path,
               COUNT(l) AS count,
               AVG(l.durationMs) AS averageDurationMs,
               MAX(l.createdAt) AS lastCalledAt
        FROM ApiRequestLog l
        WHERE l.createdAt >= :since
        GROUP BY l.method, l.path
        ORDER BY COUNT(l) DESC, MAX(l.createdAt) DESC
    """)
    List<ApiRequestUsageProjection> findTopEndpointsSince(@Param("since") LocalDateTime since, Pageable pageable);

    interface ApiRequestUsageProjection {
        String getMethod();

        String getPath();

        long getCount();

        Double getAverageDurationMs();

        LocalDateTime getLastCalledAt();
    }
}
