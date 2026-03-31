package univ.airconnect.iap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;

import java.util.Optional;

public interface IapOrderRepository extends JpaRepository<IapOrder, Long> {

    Optional<IapOrder> findByStoreAndTransactionId(IapStore store, String transactionId);

    Optional<IapOrder> findByStoreAndPurchaseToken(IapStore store, String purchaseToken);
}

