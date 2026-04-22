package univ.airconnect.user.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.entity.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndSocialId(SocialProvider provider, String socialId);

    Optional<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userProfile WHERE u.id IN :ids")
    List<User> findAllByIdWithProfile(@Param("ids") Collection<Long> ids);

    @Query("""
        SELECT count(u)
        FROM User u
        WHERE u.status = univ.airconnect.user.domain.UserStatus.ACTIVE
          AND u.onboardingStatus = univ.airconnect.user.domain.OnboardingStatus.FULL
    """)
    long countActiveSignedUpUsers();

    @Query("""
        SELECT count(u)
        FROM User u
        WHERE u.status = univ.airconnect.user.domain.UserStatus.ACTIVE
          AND u.lastActiveAt >= :startOfDay
    """)
    long countDailyActiveUsers(@Param("startOfDay") LocalDateTime startOfDay);

    // 프로필 기반 추천: 프로필이 있고 활성 상태인 사용자들
    // 제외 조건:
    // 1) 양방향 PENDING 요청 존재
    // 2) ACCEPTED 연결 + 두 사용자가 같은 PERSONAL 채팅방에 현재 모두 멤버
    @Query("""
        SELECT u
        FROM User u
        INNER JOIN u.userProfile up
        WHERE u.status = 'ACTIVE'
          AND u.id <> :userId
          AND up.gender <> (
              SELECT up2.gender FROM UserProfile up2 WHERE up2.userId = :userId
          )
          AND NOT EXISTS (
              SELECT 1 FROM MatchingConnection mc
              WHERE (
                    (mc.user1Id = :userId AND mc.user2Id = u.id)
                 OR (mc.user1Id = u.id AND mc.user2Id = :userId)
              )
                AND mc.status = 'PENDING'
          )
          AND NOT EXISTS (
              SELECT 1 FROM MatchingConnection mc
              WHERE (
                    (mc.user1Id = :userId AND mc.user2Id = u.id)
                 OR (mc.user1Id = u.id AND mc.user2Id = :userId)
              )
                AND mc.status = 'ACCEPTED'
                AND mc.chatRoomId IS NOT NULL
                AND EXISTS (
                    SELECT 1 FROM ChatRoomMember me
                    WHERE me.chatRoom.id = mc.chatRoomId
                      AND me.user.id = :userId
                )
                AND EXISTS (
                    SELECT 1 FROM ChatRoomMember other
                    WHERE other.chatRoom.id = mc.chatRoomId
                      AND other.user.id = u.id
                )
          )
        ORDER BY u.createdAt ASC
    """)
    List<User> findActiveUsersWithProfileForMatching(@Param("userId") Long userId);

    @Query("""
        SELECT u
        FROM User u
        INNER JOIN u.userProfile up
        WHERE u.status = 'ACTIVE'
          AND u.id <> :userId
          AND up.gender = (
              SELECT up2.gender FROM UserProfile up2 WHERE up2.userId = :userId
          )
          AND NOT EXISTS (
              SELECT 1 FROM MatchingConnection mc
              WHERE (
                    (mc.user1Id = :userId AND mc.user2Id = u.id)
                 OR (mc.user1Id = u.id AND mc.user2Id = :userId)
              )
                AND mc.status = 'PENDING'
          )
          AND NOT EXISTS (
              SELECT 1 FROM MatchingConnection mc
              WHERE (
                    (mc.user1Id = :userId AND mc.user2Id = u.id)
                 OR (mc.user1Id = u.id AND mc.user2Id = :userId)
              )
                AND mc.status = 'ACCEPTED'
                AND mc.chatRoomId IS NOT NULL
                AND EXISTS (
                    SELECT 1 FROM ChatRoomMember me
                    WHERE me.chatRoom.id = mc.chatRoomId
                      AND me.user.id = :userId
                )
                AND EXISTS (
                    SELECT 1 FROM ChatRoomMember other
                    WHERE other.chatRoom.id = mc.chatRoomId
                      AND other.user.id = u.id
                )
          )
        ORDER BY u.createdAt ASC
    """)
    List<User> findActiveUsersWithProfileForSameGenderMatching(@Param("userId") Long userId);
}
