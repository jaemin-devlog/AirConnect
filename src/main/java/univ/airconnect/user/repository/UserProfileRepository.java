package univ.airconnect.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.statistics.repository.GenderCountProjection;
import univ.airconnect.user.domain.entity.UserProfile;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    @Query("""
        SELECT up
        FROM UserProfile up
        JOIN FETCH up.user u
        WHERE up.profileImagePath = :profileImagePath
    """)
    Optional<UserProfile> findByProfileImagePathWithUser(@Param("profileImagePath") String profileImagePath);

    @Query("""
        SELECT up.gender as gender, count(up) as count
        FROM UserProfile up
        JOIN up.user u
        WHERE u.status = univ.airconnect.user.domain.UserStatus.ACTIVE
          AND u.onboardingStatus = univ.airconnect.user.domain.OnboardingStatus.FULL
          AND up.gender IS NOT NULL
        GROUP BY up.gender
    """)
    List<GenderCountProjection> countActiveSignedUpUsersByGender();
}
