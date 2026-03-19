package univ.airconnect.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.matching.domain.entity.MatchingExposure;

public interface MatchingExposureRepository extends JpaRepository<MatchingExposure, Long> {

    boolean existsByUserIdAndCandidateUserId(Long userId, Long candidateUserId);

    void deleteByUserId(Long userId);
}

