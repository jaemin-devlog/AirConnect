package univ.airconnect.groupmatching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GTemporaryTeamRoomRepository extends JpaRepository<GTemporaryTeamRoom, Long> {

    /**
     * 임시 팀방 비관적 락 조회
     * - 팀원 추가/제거
     * - 큐 진입/이탈
     * - 매칭 완료 처리
     * 같은 경쟁 구간에서 사용 권장
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from GTemporaryTeamRoom t
            where t.id = :teamRoomId
            """)
    Optional<GTemporaryTeamRoom> findByIdForUpdate(@Param("teamRoomId") Long teamRoomId);

    Optional<GTemporaryTeamRoom> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    long countByQueuedAtGreaterThanEqual(LocalDateTime since);

    @Query(value = """
            SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, queued_at, matched_at)), 0)
            FROM matching_temporary_team_rooms
            WHERE queued_at IS NOT NULL
              AND matched_at IS NOT NULL
              AND queued_at >= :since
            """, nativeQuery = true)
    Double averageQueueWaitSecondsSince(@Param("since") LocalDateTime since);

    /**
     * 방장이 현재 살아 있는 임시 팀방을 하나라도 가지고 있는지 확인
     * active status = OPEN, READY_CHECK, QUEUE_WAITING, MATCHED
     */
    @Query("""
            select case when count(t) > 0 then true else false end
            from GTemporaryTeamRoom t
            where t.leaderId = :leaderId
              and t.status in :activeStatuses
            """)
    boolean existsActiveRoomByLeaderId(
            @Param("leaderId") Long leaderId,
            @Param("activeStatuses") Collection<GTemporaryTeamRoomStatus> activeStatuses
    );

    /**
     * 특정 유저가 현재 속한 살아 있는 임시 팀방 조회
     * - leftAt is null 인 활성 멤버 기준
     */
    @Query("""
            select t
            from GTemporaryTeamRoom t
            join GTemporaryTeamMember m on m.teamRoomId = t.id
            where m.userId = :userId
              and m.leftAt is null
              and t.status in :activeStatuses
            order by t.createdAt desc
            """)
    List<GTemporaryTeamRoom> findActiveRoomsByUserId(
            @Param("userId") Long userId,
            @Param("activeStatuses") Collection<GTemporaryTeamRoomStatus> activeStatuses
    );

    // 사용자 행을 잠근 뒤 현재 읽기로 확인하여 동시 생성/코드 입장의 중복 소속을 방지한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from GTemporaryTeamRoom t
            join GTemporaryTeamMember m on m.teamRoomId = t.id
            where m.userId = :userId and m.leftAt is null and t.status in :activeStatuses
            """)
    List<GTemporaryTeamRoom> findActiveRoomsByUserIdForUpdate(
            @Param("userId") Long userId,
            @Param("activeStatuses") Collection<GTemporaryTeamRoomStatus> activeStatuses
    );

    /**
     * 현재 큐 대기 중인 팀을 DB 기준 순서대로 모두 조회한다.
     * Redis 유실 또는 재구성이 필요할 때 기준 목록으로 사용한다.
     */
    @Query("""
            select t
            from GTemporaryTeamRoom t
            where t.status = univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus.QUEUE_WAITING
              and t.teamSize = :teamSize
            order by t.queuedAt asc, t.id asc
            """)
    List<GTemporaryTeamRoom> findAllQueueWaitingRooms(@Param("teamSize") GTeamSize teamSize);

}
