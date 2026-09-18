package univ.airconnect.statistics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.department.repository.DepartmentRankingProjection;
import univ.airconnect.department.repository.DepartmentRepository;
import univ.airconnect.statistics.domain.entity.DepartmentRankingBaseline;
import univ.airconnect.statistics.repository.DepartmentRankingBaselineRepository;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentRankingSnapshotServiceTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock DepartmentRankingBaselineRepository baselineRepository;

    @Test
    void refreshStoresAllRankingsAndTopThreeUsesSameSnapshot() {
        DepartmentRankingSnapshotService service =
                new DepartmentRankingSnapshotService(departmentRepository, baselineRepository);
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 19, 0, 0);
        when(baselineRepository.findById(DepartmentRankingBaselineInitializer.BASELINE_ID))
                .thenReturn(Optional.of(DepartmentRankingBaseline.start(1L, startedAt)));
        when(departmentRepository.findAllRankedByMatchingRequests(startedAt)).thenReturn(List.of(
                projection(1L, "시각디자인학과", 56L),
                projection(2L, "항공운항학과", 41L),
                projection(3L, "간호학과", 41L),
                projection(4L, "항공소프트웨어공학과", 30L)
        ));

        List<DepartmentRankingResponse> refreshed = service.refresh();

        assertThat(refreshed).extracting(DepartmentRankingResponse::getRank)
                .containsExactly(1, 2, 2, 4);
        assertThat(service.getTopThree()).hasSize(3);
        assertThat(service.getTopThree().get(2).rank()).isEqualTo(2);
        verify(departmentRepository, times(1)).findAllRankedByMatchingRequests(startedAt);
    }

    @Test
    void firstReadBuildsSnapshotWhenSchedulerHasNotRunYet() {
        DepartmentRankingSnapshotService service =
                new DepartmentRankingSnapshotService(departmentRepository, baselineRepository);
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 19, 0, 0);
        when(baselineRepository.findById(DepartmentRankingBaselineInitializer.BASELINE_ID))
                .thenReturn(Optional.of(DepartmentRankingBaseline.start(1L, startedAt)));
        when(departmentRepository.findAllRankedByMatchingRequests(startedAt)).thenReturn(List.of());

        assertThat(service.getRankings()).isEmpty();
        assertThat(service.getRankings()).isEmpty();

        verify(departmentRepository, times(1)).findAllRankedByMatchingRequests(startedAt);
    }

    private DepartmentRankingProjection projection(Long id, String name, long requestCount) {
        return new DepartmentRankingProjection() {
            @Override
            public Long getDepartmentId() {
                return id;
            }

            @Override
            public String getDeptName() {
                return name;
            }

            @Override
            public String getCollegeName() {
                return "학부";
            }

            @Override
            public String getStatus() {
                return "ACTIVE";
            }

            @Override
            public long getRequestCount() {
                return requestCount;
            }
        };
    }
}
