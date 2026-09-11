package univ.airconnect.statistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.department.repository.DepartmentRankingProjection;
import univ.airconnect.department.repository.DepartmentRepository;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentRankingSnapshotService {

    private final DepartmentRepository departmentRepository;

    private volatile List<DepartmentRankingResponse> rankings = List.of();
    private volatile boolean initialized;

    @Transactional(readOnly = true)
    public synchronized List<DepartmentRankingResponse> refresh() {
        List<DepartmentRankingProjection> projections =
                departmentRepository.findAllRankedByMatchingRequests();
        List<DepartmentRankingResponse> refreshed = new ArrayList<>(projections.size());
        int rank = 0;
        long previousCount = Long.MIN_VALUE;
        for (int i = 0; i < projections.size(); i++) {
            DepartmentRankingProjection projection = projections.get(i);
            if (projection.getRequestCount() != previousCount) {
                rank = i + 1;
                previousCount = projection.getRequestCount();
            }
            refreshed.add(DepartmentRankingResponse.builder()
                    .rank(rank)
                    .departmentId(projection.getDepartmentId())
                    .deptName(projection.getDeptName())
                    .collegeName(projection.getCollegeName())
                    .status(projection.getStatus())
                    .requestCount(projection.getRequestCount())
                    .build());
        }

        rankings = List.copyOf(refreshed);
        initialized = true;
        return rankings;
    }

    public List<DepartmentRankingResponse> getRankings() {
        return initialized ? rankings : refresh();
    }

    public List<RealtimeMainStatisticsResponse.TopDepartment> getTopThree() {
        return getRankings().stream()
                .limit(3)
                .map(ranking -> new RealtimeMainStatisticsResponse.TopDepartment(
                        ranking.getRank(),
                        ranking.getDepartmentId(),
                        ranking.getDeptName(),
                        ranking.getRequestCount()
                ))
                .toList();
    }
}
