package univ.airconnect.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.matching.domain.entity.MatchingRecommendationRequest;

import java.util.Optional;

public interface MatchingRecommendationRequestRepository
        extends JpaRepository<MatchingRecommendationRequest, Long> {

    Optional<MatchingRecommendationRequest> findByUserIdAndRequestKey(Long userId, String requestKey);
}
