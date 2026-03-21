package univ.airconnect.groupmatching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;

import java.util.List;
import java.util.Optional;

@Repository
public interface GTeamReadyStateRepository extends JpaRepository<GTeamReadyState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from GTeamReadyState r
            where r.teamRoomId = :teamRoomId
            order by r.id asc
            """)
    List<GTeamReadyState> findAllByTeamRoomIdForUpdate(@Param("teamRoomId") Long teamRoomId);

    List<GTeamReadyState> findByTeamRoomIdOrderByIdAsc(Long teamRoomId);

    Optional<GTeamReadyState> findByTeamRoomIdAndUserId(Long teamRoomId, Long userId);

    boolean existsByTeamRoomIdAndUserId(Long teamRoomId, Long userId);

    long countByTeamRoomId(Long teamRoomId);

    long countByTeamRoomIdAndReadyTrue(Long teamRoomId);

    void deleteByTeamRoomId(Long teamRoomId);

    /**
     * 팀원 전원이 준비 완료인지 확인
     * - 준비 상태 row 개수 == 팀 정원
     * - ready=true 개수 == 팀 정원
     */
    @Query("""
            select case when count(r) = :expectedCount
                         and sum(case when r.ready = true then 1 else 0 end) = :expectedCount
                        then true else false end
            from GTeamReadyState r
            where r.teamRoomId = :teamRoomId
            """)
    boolean areAllMembersReady(
            @Param("teamRoomId") Long teamRoomId,
            @Param("expectedCount") long expectedCount
    );
}
