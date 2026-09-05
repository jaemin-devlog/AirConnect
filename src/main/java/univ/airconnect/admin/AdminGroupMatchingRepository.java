package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.entity.*;

import java.util.List;

/** Diagnostic queries only: no locks, recovery, or write operations. */
public interface AdminGroupMatchingRepository extends Repository<GTemporaryTeamRoom, Long> {
    @Query("""
            select t from GTemporaryTeamRoom t
            where (:teamId is null or t.id = :teamId)
              and (:status is null or t.status = :status)
              and (:userId is null or t.leaderId = :userId or exists
                (select m.id from GTemporaryTeamMember m where m.teamRoomId = t.id and m.userId = :userId))
            order by t.id desc
            """)
    Page<GTemporaryTeamRoom> search(@Param("userId") Long userId, @Param("teamId") Long teamId,
                                   @Param("status") GTemporaryTeamRoomStatus status, Pageable page);

    @Query("""
            select t from GTemporaryTeamRoom t
            where t.status = univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus.QUEUE_WAITING
              and t.teamSize = :teamSize and t.teamGender = :gender
              and (:userId is null or t.leaderId = :userId or exists
                (select m.id from GTemporaryTeamMember m where m.teamRoomId = t.id
                 and m.userId = :userId and m.leftAt is null))
            order by case when t.queuedAt is null then 1 else 0 end, t.queuedAt, t.id
            """)
    Page<GTemporaryTeamRoom> waiting(@Param("teamSize") GTeamSize teamSize, @Param("gender") GTeamGender gender,
                                    @Param("userId") Long userId, Pageable page);

    interface ActiveCount {
        Long getTeamId();
        long getMemberCount();
    }

    @Query("""
            select m.teamRoomId as teamId, count(m) as memberCount from GTemporaryTeamMember m
            where m.teamRoomId in :teamIds and m.leftAt is null group by m.teamRoomId
            """)
    List<ActiveCount> activeCounts(@Param("teamIds") List<Long> teamIds);

    @Query("select r from GMatchResult r where r.team1RoomId = :teamId or r.team2RoomId = :teamId order by r.id desc")
    List<GMatchResult> results(@Param("teamId") Long teamId, Pageable limit);

    @Query("select f from GFinalGroupChatRoom f where f.team1RoomId = :teamId or f.team2RoomId = :teamId order by f.id desc")
    List<GFinalGroupChatRoom> finalRooms(@Param("teamId") Long teamId, Pageable limit);
}
