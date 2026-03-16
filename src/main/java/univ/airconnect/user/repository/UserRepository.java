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

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userProfile WHERE u.id IN :ids")
    List<User> findAllByIdWithProfile(@Param("ids") Collection<Long> ids);
}