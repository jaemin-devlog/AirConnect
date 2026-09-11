package univ.airconnect.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.matching.domain.entity.MatchingRecommendationRequest;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MatchingRecommendationRequestRepository
        extends JpaRepository<MatchingRecommendationRequest, Long> {

    Optional<MatchingRecommendationRequest> findByUserIdAndRequestKey(Long userId, String requestKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM MatchingRecommendationRequest r
        WHERE r.userId = :userId
           OR r.candidateUserIds = :candidateId
           OR r.candidateUserIds LIKE CONCAT(:candidateId, ',%')
           OR r.candidateUserIds LIKE CONCAT('%,', :candidateId)
           OR r.candidateUserIds LIKE CONCAT(CONCAT('%,', :candidateId), ',%')
    """)
    int deleteByOwnerOrCandidate(@Param("userId") Long userId,
                                 @Param("candidateId") String candidateId);

    int deleteByCreatedAtBefore(LocalDateTime cutoff);
}
