package univ.airconnect.user.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.user.domain.entity.User;

import java.util.Optional;

public class UserTicketLockRepositoryImpl implements UserTicketLockRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<User> findByIdForTicketUpdate(Long userId) {
        // Keep pending profile/account changes in this same transaction before refresh.
        // User's DynamicUpdate prevents those changes from writing a stale ticket balance.
        entityManager.flush();
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            return Optional.empty();
        }
        // A locking query alone can return an already-managed, stale User. Refresh both
        // its values and JPA snapshot under the lock, including when net change is zero.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(user);
    }
}
