package univ.airconnect.auth.repository;

import org.springframework.data.repository.CrudRepository;
import univ.airconnect.auth.domain.entity.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
    Iterable<RefreshToken> findByUserId(Long userId);
}