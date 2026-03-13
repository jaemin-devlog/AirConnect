package univ.airconnect.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.user.domain.entity.UserProfile;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);
}