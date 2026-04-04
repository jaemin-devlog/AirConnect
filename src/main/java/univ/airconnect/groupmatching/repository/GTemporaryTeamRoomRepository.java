package univ.airconnect.groupmatching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GTemporaryTeamRoomRepository extends JpaRepository<GTemporaryTeamRoom, Long> {

    /**
     * 임시 팀방 비관적 락 조회
     * - 팀원 추가/제거
     * - 준비 상태 전환
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

    Optional<GTemporaryTeamRoom> findByTempChatRoomId(Long tempChatRoomId);

    Optional<GTemporaryTeamRoom> findByQueueToken(String queueToken);

    List<GTemporaryTeamRoom> findByStatus(GTemporaryTeamRoomStatus status);

    List<GTemporaryTeamRoom> findByStatusIn(Collection<GTemporaryTeamRoomStatus> statuses);

    boolean existsByLeaderIdAndStatusIn(Long leaderId, Collection<GTemporaryTeamRoomStatus> statuses);

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

    /**
     * 공개 모집 중인 임시 팀방 목록 조회
     * - 정원 미달 여부는 서비스에서 t.isFull()로 한 번 더 거르는 것을 권장
     */
    @Query(
            value = """
                    select t
                    from GTemporaryTeamRoom t
                    where t.status = :status
                      and t.teamSize = :teamSize
                      and (
                          (t.teamSize = univ.airconnect.groupmatching.domain.GTeamSize.TWO and t.currentMemberCount < 2)
                          or (t.teamSize = univ.airconnect.groupmatching.domain.GTeamSize.THREE and t.currentMemberCount < 3)
                      )
                    order by t.createdAt asc, t.id asc
                    """,
            countQuery = """
                    select count(t)
                    from GTemporaryTeamRoom t
                    where t.status = :status
                      and t.teamSize = :teamSize
                      and (
                          (t.teamSize = univ.airconnect.groupmatching.domain.GTeamSize.TWO and t.currentMemberCount < 2)
                          or (t.teamSize = univ.airconnect.groupmatching.domain.GTeamSize.THREE and t.currentMemberCount < 3)
                      )
                    """
    )
    Page<GTemporaryTeamRoom> findRecruitableRooms(
            @Param("status") GTemporaryTeamRoomStatus status,
            @Param("teamSize") GTeamSize teamSize,
            Pageable pageable
    );

    @Query("""
            select count(t)
            from GTemporaryTeamRoom t
            where t.status = :status
              and (
                  (t.teamSize = univ.airconnect.groupmatching.domain.GTeamSize.TWO and t.currentMemberCount < 2)
                  or (t.teamSize = univ.airconnect.groupmatching.domain.GTeamSize.THREE and t.currentMemberCount < 3)
              )
            """)
    long countRecruitableRooms(@Param("status") GTemporaryTeamRoomStatus status);

    /**
     * 큐 대기 중인 팀 후보를 오래 기다린 순으로 조회
     * - 실제 상대 팀 선택은 서비스에서 canMatchWith()로 판정
     * - 동시 매칭 충돌 방지를 위해 락 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from GTemporaryTeamRoom t
            where t.status = univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus.QUEUE_WAITING
              and t.teamSize = :teamSize
            order by t.queuedAt asc, t.id asc
            """)
    List<GTemporaryTeamRoom> findQueueWaitingRoomsForUpdate(
            @Param("teamSize") GTeamSize teamSize,
            Pageable pageable
    );

    /**
     * 큐 대기 중인 팀 중 가장 오래된 팀 하나 조회
     */
    @Query("""
            select t
            from GTemporaryTeamRoom t
            where t.status = univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus.QUEUE_WAITING
              and t.teamSize = :teamSize
            order by t.queuedAt asc, t.id asc
            """)
    List<GTemporaryTeamRoom> findOldestQueueWaitingRooms(
            @Param("teamSize") GTeamSize teamSize,
            Pageable pageable
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

    /**
     * 매칭/종료되지 않은 방 중 inviteCode 중복 여부 확인용
     */
    @Query("""
            select case when count(t) > 0 then true else false end
            from GTemporaryTeamRoom t
            where t.inviteCode = :inviteCode
              and t.status <> univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus.CANCELLED
              and t.status <> univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus.CLOSED
            """)
    boolean existsUsableInviteCode(@Param("inviteCode") String inviteCode);
}
