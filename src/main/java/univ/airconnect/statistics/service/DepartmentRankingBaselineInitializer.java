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

    private final DepartmentRankingBaselineRepository baselineRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!baselineRepository.existsById(BASELINE_ID)) {
            baselineRepository.save(DepartmentRankingBaseline.start(BASELINE_ID, LocalDateTime.now()));
        }
    }
}
