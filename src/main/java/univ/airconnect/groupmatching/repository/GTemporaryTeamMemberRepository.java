package univ.airconnect.groupmatching.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;

import java.util.List;
import java.util.Optional;

@Repository
public interface GTemporaryTeamMemberRepository extends JpaRepository<GTemporaryTeamMember, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from GTemporaryTeamMember m
            where m.id = :memberId
            """)
    Optional<GTemporaryTeamMember> findByIdForUpdate(@Param("memberId") Long memberId);

    Optional<GTemporaryTeamMember> findByTeamRoomIdAndUserId(Long teamRoomId, Long userId);

    boolean existsByTeamRoomIdAndUserId(Long teamRoomId, Long userId);

    boolean existsByTeamRoomIdAndUserIdAndLeftAtIsNull(Long teamRoomId, Long userId);

    boolean existsByUserIdAndLeftAtIsNull(Long userId);

    long countByTeamRoomIdAndLeftAtIsNull(Long teamRoomId);

    List<GTemporaryTeamMember> findByTeamRoomIdOrderByJoinedAtAsc(Long teamRoomId);

    List<GTemporaryTeamMember> findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(Long teamRoomId);

    Optional<GTemporaryTeamMember> findByTeamRoomIdAndLeaderTrueAndLeftAtIsNull(Long teamRoomId);

    @Query("""
            select m
            from GTemporaryTeamMember m
            where m.userId = :userId
              and m.leftAt is null
            order by m.joinedAt desc
            """)
    List<GTemporaryTeamMember> findActiveMembershipsByUserId(@Param("userId") Long userId);
}
