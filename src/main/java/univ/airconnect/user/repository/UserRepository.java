package univ.airconnect.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndSocialId(SocialProvider provider, String socialId);
}