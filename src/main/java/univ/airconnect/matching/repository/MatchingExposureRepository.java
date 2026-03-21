package univ.airconnect.matching.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.matching.domain.entity.MatchingExposure;

public interface MatchingExposureRepository extends JpaRepository<MatchingExposure, Long> {

    boolean existsByUserIdAndCandidateUserId(Long userId, Long candidateUserId);

    void deleteByUserId(Long userId);


    @Query("""
            SELECT me.candidateUserId
            FROM MatchingExposure me
            WHERE me.userId = :userId
            """)
    List<Long> findCandidateUserIdsByUserId(@Param("userId") Long userId);
}

