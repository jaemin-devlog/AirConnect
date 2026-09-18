package univ.airconnect.statistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.statistics.domain.entity.DepartmentRankingBaseline;
import univ.airconnect.statistics.repository.DepartmentRankingBaselineRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DepartmentRankingBaselineInitializer implements ApplicationRunner {

    public static final long BASELINE_ID = 1L;
    public static final long ADMIN_MATCHING_BASELINE_ID = 2L;

    private final DepartmentRankingBaselineRepository baselineRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime startedAt = LocalDateTime.now();
        initialize(BASELINE_ID, startedAt);
        initialize(ADMIN_MATCHING_BASELINE_ID, startedAt);
    }

    private void initialize(long id, LocalDateTime startedAt) {
        if (!baselineRepository.existsById(id)) {
            baselineRepository.save(DepartmentRankingBaseline.start(id, startedAt));
        }
    }
}
