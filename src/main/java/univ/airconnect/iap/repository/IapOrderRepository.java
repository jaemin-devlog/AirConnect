package univ.airconnect.iap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface IapOrderRepository extends JpaRepository<IapOrder, Long> {

    List<IapOrder> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<IapOrder> findByStoreAndTransactionId(IapStore store, String transactionId);

    Optional<IapOrder> findByStoreAndPurchaseToken(IapStore store, String purchaseToken);

    List<IapOrder> findByStoreAndOriginalTransactionIdAndUserId(IapStore store, String originalTransactionId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM IapOrder o WHERE o.id = :orderId")
    Optional<IapOrder> findByIdForUpdate(@Param("orderId") Long orderId);
}
