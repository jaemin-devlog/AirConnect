package univ.airconnect.statistics.service;

import org.junit.jupiter.api.Test;
import univ.airconnect.statistics.repository.DepartmentRankingBaselineRepository;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentRankingBaselineInitializerTest {

    @Test
    void createsBaselineOnlyOnFirstDeployment() {
        DepartmentRankingBaselineRepository repository = mock(DepartmentRankingBaselineRepository.class);
        when(repository.existsById(DepartmentRankingBaselineInitializer.BASELINE_ID)).thenReturn(false);

        new DepartmentRankingBaselineInitializer(repository).run(null);

        verify(repository).save(argThat(baseline ->
                baseline.getId().equals(DepartmentRankingBaselineInitializer.BASELINE_ID)
                        && baseline.getStartedAt() != null));
    }

    @Test
    void preservesExistingBaselineAcrossRestarts() {
        DepartmentRankingBaselineRepository repository = mock(DepartmentRankingBaselineRepository.class);
        when(repository.existsById(DepartmentRankingBaselineInitializer.BASELINE_ID)).thenReturn(true);

        new DepartmentRankingBaselineInitializer(repository).run(null);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
