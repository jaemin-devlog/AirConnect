package univ.airconnect.auth.repository;

import univ.airconnect.auth.domain.entity.RefreshToken;

public interface RefreshTokenUserLookup {
    Iterable<RefreshToken> findByUserId(Long userId);
}
