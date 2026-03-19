package univ.airconnect.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.matching.domain.entity.MatchingExposure;

public interface MatchingExposureRepository extends JpaRepository<MatchingExposure, Long> {

    boolean existsByUserIdAndCandidateUserId(Long userId, Long candidateUserId);

    void deleteByUserId(Long userId);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO matching_exposures (user_id, candidate_user_id, exposed_at)
            VALUES (:userId, :candidateUserId, NOW())
            """, nativeQuery = true)
    void insertIgnore(@Param("userId") Long userId, @Param("candidateUserId") Long candidateUserId);
}

