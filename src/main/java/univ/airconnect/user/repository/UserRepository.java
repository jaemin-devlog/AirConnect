package univ.airconnect.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndSocialId(SocialProvider provider, String socialId);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userProfile WHERE u.id IN :ids")
    List<User> findAllByIdWithProfile(@Param("ids") Collection<Long> ids);

    // 프로필 기반 추천: 프로필이 있고 활성 상태인 사용자들 (본인 제외, 이미 매칭된 사람 제외)
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
              SELECT 1 FROM MatchingExposure me
              WHERE me.userId = :userId AND me.candidateUserId = u.id
          )
          AND NOT EXISTS (
              SELECT 1 FROM MatchingConnection mc
              WHERE (mc.user1Id = :userId AND mc.user2Id = u.id)
                 OR (mc.user1Id = u.id AND mc.user2Id = :userId)
          )
        ORDER BY u.createdAt ASC
    """)
    List<User> findActiveUsersWithProfileForMatching(
            @Param("userId") Long userId,
            org.springframework.data.domain.Pageable pageable
    );
}