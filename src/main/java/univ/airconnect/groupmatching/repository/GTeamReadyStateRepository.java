package univ.airconnect.groupmatching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;

import java.util.List;
import java.time.LocalDateTime;
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

    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT r.team_room_id
                FROM matching_team_ready_states r
                JOIN matching_temporary_team_rooms t
                  ON t.id = r.team_room_id
                WHERE r.is_ready = true
                  AND r.ready_at >= :since
                GROUP BY r.team_room_id, t.team_size
                HAVING COUNT(*) >= CASE
                    WHEN t.team_size = 'TWO' THEN 2
                    WHEN t.team_size = 'THREE' THEN 3
                    ELSE 999999
                END
            ) ready_teams
            """, nativeQuery = true)
    long countReadyTeamsSince(@Param("since") LocalDateTime since);

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
