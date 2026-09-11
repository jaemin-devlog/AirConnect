package univ.airconnect.groupmatching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface GTemporaryTeamMemberRepository extends JpaRepository<GTemporaryTeamMember, Long> {

    Optional<GTemporaryTeamMember> findByTeamRoomIdAndUserId(Long teamRoomId, Long userId);

    boolean existsByTeamRoomIdAndUserIdAndLeftAtIsNull(Long teamRoomId, Long userId);

    long countByTeamRoomIdAndLeftAtIsNull(Long teamRoomId);

    long countByJoinedAtGreaterThanEqual(LocalDateTime since);

    List<GTemporaryTeamMember> findByTeamRoomIdOrderByJoinedAtAsc(Long teamRoomId);

    List<GTemporaryTeamMember> findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(Long teamRoomId);
}
