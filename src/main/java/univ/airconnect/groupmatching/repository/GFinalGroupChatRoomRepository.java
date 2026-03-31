package univ.airconnect.groupmatching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.GFinalGroupRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GFinalGroupChatRoomRepository extends JpaRepository<GFinalGroupChatRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f
            from GFinalGroupChatRoom f
            where f.id = :finalGroupRoomId
            """)
    Optional<GFinalGroupChatRoom> findByIdForUpdate(@Param("finalGroupRoomId") Long finalGroupRoomId);

    Optional<GFinalGroupChatRoom> findByChatRoomId(Long chatRoomId);

    Optional<GFinalGroupChatRoom> findByMatchResultId(Long matchResultId);

    boolean existsByChatRoomId(Long chatRoomId);

    boolean existsByMatchResultId(Long matchResultId);

    List<GFinalGroupChatRoom> findByStatus(GFinalGroupRoomStatus status);

    List<GFinalGroupChatRoom> findByStatusIn(Collection<GFinalGroupRoomStatus> statuses);

    /**
     * team pair 역순까지 포함한 최종 그룹방 조회
     */
    @Query("""
            select f
            from GFinalGroupChatRoom f
            where (f.team1RoomId = :teamA and f.team2RoomId = :teamB)
               or (f.team1RoomId = :teamB and f.team2RoomId = :teamA)
            """)
    Optional<GFinalGroupChatRoom> findByTeamPair(@Param("teamA") Long teamA, @Param("teamB") Long teamB);

    /**
     * 특정 임시 팀방이 포함된 활성 최종 그룹방 조회
     */
    @Query("""
            select f
            from GFinalGroupChatRoom f
            where (f.team1RoomId = :teamRoomId or f.team2RoomId = :teamRoomId)
              and f.status = univ.airconnect.groupmatching.domain.GFinalGroupRoomStatus.ACTIVE
            order by f.createdAt desc, f.id desc
            """)
    List<GFinalGroupChatRoom> findActiveRoomsByTeamRoomId(@Param("teamRoomId") Long teamRoomId);

    /**
     * 특정 사용자가 속했던 임시 팀방을 기반으로 활성 최종 그룹방을 조회한다.
     * 앱 재실행 시 현재 사용자를 어느 화면으로 복구할지 판단할 때 사용한다.
     */
    @Query("""
            select f
            from GFinalGroupChatRoom f
            where f.status = univ.airconnect.groupmatching.domain.GFinalGroupRoomStatus.ACTIVE
              and exists (
                    select 1
                    from GTemporaryTeamMember m
                    where m.userId = :userId
                      and (m.teamRoomId = f.team1RoomId or m.teamRoomId = f.team2RoomId)
              )
            order by f.createdAt desc, f.id desc
            """)
    List<GFinalGroupChatRoom> findActiveRoomsByUserId(@Param("userId") Long userId);
}
