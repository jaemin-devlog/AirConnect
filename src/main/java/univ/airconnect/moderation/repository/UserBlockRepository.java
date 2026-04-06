package univ.airconnect.moderation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.moderation.domain.entity.UserBlock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    List<UserBlock> findByBlockerUserIdOrderByCreatedAtDesc(Long blockerUserId);

    @Query("""
        SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
        FROM UserBlock b
        WHERE (b.blockerUserId = :userAId AND b.blockedUserId = :userBId)
           OR (b.blockerUserId = :userBId AND b.blockedUserId = :userAId)
    """)
    boolean existsRelation(@Param("userAId") Long userAId, @Param("userBId") Long userBId);

    @Query("""
        SELECT b.blockedUserId
        FROM UserBlock b
        WHERE b.blockerUserId = :userId
          AND b.blockedUserId IN :targetUserIds
    """)
    List<Long> findBlockedUserIds(@Param("userId") Long userId, @Param("targetUserIds") Collection<Long> targetUserIds);

    @Query("""
        SELECT b.blockerUserId
        FROM UserBlock b
        WHERE b.blockedUserId = :userId
          AND b.blockerUserId IN :targetUserIds
    """)
    List<Long> findBlockerUserIds(@Param("userId") Long userId, @Param("targetUserIds") Collection<Long> targetUserIds);
}
