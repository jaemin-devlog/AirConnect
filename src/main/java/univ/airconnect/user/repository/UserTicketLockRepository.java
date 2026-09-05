package univ.airconnect.user.repository;

import univ.airconnect.user.domain.entity.User;

import java.util.Optional;

public interface UserTicketLockRepository {
    /** Must be called inside the transaction that changes tickets and saves their history. */
    Optional<User> findByIdForTicketUpdate(Long userId);
}
