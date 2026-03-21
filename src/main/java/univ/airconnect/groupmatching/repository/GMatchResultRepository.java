package univ.airconnect.groupmatching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.GMatchResultStatus;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GMatchResultRepository extends JpaRepository<GMatchResult, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mr
            from GMatchResult mr
            where mr.id = :matchResultId
            """)
    Optional<GMatchResult> findByIdForUpdate(@Param("matchResultId") Long matchResultId);

    Optional<GMatchResult> findByFinalGroupChatRoomId(Long finalGroupChatRoomId);

    List<GMatchResult> findByStatus(GMatchResultStatus status);

    List<GMatchResult> findByStatusIn(Collection<GMatchResultStatus> statuses);

    /**
     * team pair 역순까지 포함해서 동일 매칭 조회
     * - 서비스에서 작은 ID/큰 ID 정규화 후 호출해도 되고
     * - 그대로 호출해도 이 쿼리가 양방향을 커버한다.
     */
    @Query("""
            select mr
            from GMatchResult mr
            where (mr.team1RoomId = :teamA and mr.team2RoomId = :teamB)
               or (mr.team1RoomId = :teamB and mr.team2RoomId = :teamA)
            """)
    Optional<GMatchResult> findByTeamPair(@Param("teamA") Long teamA, @Param("teamB") Long teamB);

    @Query("""
            select case when count(mr) > 0 then true else false end
            from GMatchResult mr
            where ((mr.team1RoomId = :teamA and mr.team2RoomId = :teamB)
               or  (mr.team1RoomId = :teamB and mr.team2RoomId = :teamA))
              and mr.status in :statuses
            """)
    boolean existsByTeamPairAndStatuses(
            @Param("teamA") Long teamA,
            @Param("teamB") Long teamB,
            @Param("statuses") Collection<GMatchResultStatus> statuses
    );

    @Query("""
            select mr
            from GMatchResult mr
            where mr.team1RoomId = :teamRoomId
               or mr.team2RoomId = :teamRoomId
            order by mr.matchedAt desc, mr.id desc
            """)
    List<GMatchResult> findAllByTeamRoomIdOrderByMatchedAtDesc(@Param("teamRoomId") Long teamRoomId);

    @Query("""
            select mr
            from GMatchResult mr
            where (mr.team1RoomId = :teamRoomId or mr.team2RoomId = :teamRoomId)
              and mr.status in :statuses
            order by mr.matchedAt desc, mr.id desc
            """)
    List<GMatchResult> findByTeamRoomIdAndStatuses(
            @Param("teamRoomId") Long teamRoomId,
            @Param("statuses") Collection<GMatchResultStatus> statuses
    );
}
