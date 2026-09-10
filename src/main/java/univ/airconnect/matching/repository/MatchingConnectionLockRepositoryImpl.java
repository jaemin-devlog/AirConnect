package univ.airconnect.matching.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.matching.domain.entity.MatchingConnection;

import java.util.Optional;

public class MatchingConnectionLockRepositoryImpl implements MatchingConnectionLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MatchingConnection> findByIdForUpdate(Long connectionId) {
        entityManager.flush();
        MatchingConnection connection = entityManager.find(
                MatchingConnection.class,
                connectionId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (connection == null) {
            return Optional.empty();
        }
        // 잠금 전에 조회한 snapshot이 영속성 컨텍스트에 있어도 최신 DB 상태로 교체한다.
        entityManager.refresh(connection, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(connection);
    }
}
