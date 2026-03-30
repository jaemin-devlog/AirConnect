package univ.airconnect.iap.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.dto.response.IapOrderResponse;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.repository.IapOrderRepository;

import java.time.ZoneOffset;

@Service
public class IapQueryService {

    private final IapOrderRepository iapOrderRepository;

    public IapQueryService(IapOrderRepository iapOrderRepository) {
        this.iapOrderRepository = iapOrderRepository;
    }

    @Transactional(readOnly = true)
    public IapOrderResponse getAppleTransaction(Long userId, String transactionId) {
        IapOrder order = iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, transactionId)
                .orElseThrow(() -> new IapException(IapErrorCode.IAP_ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new IapException(IapErrorCode.IAP_FORBIDDEN);
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public IapOrderResponse getGooglePurchase(Long userId, String purchaseToken) {
        IapOrder order = iapOrderRepository.findByStoreAndPurchaseToken(IapStore.GOOGLE, purchaseToken)
                .orElseThrow(() -> new IapException(IapErrorCode.IAP_ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new IapException(IapErrorCode.IAP_FORBIDDEN);
        }
        return toResponse(order);
    }

    private IapOrderResponse toResponse(IapOrder order) {
        return IapOrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .store(order.getStore())
                .productId(order.getProductId())
                .transactionId(order.getTransactionId())
                .purchaseToken(order.getPurchaseToken())
                .orderId(order.getOrderId())
                .environment(order.getEnvironment())
                .status(order.getStatus())
                .grantedTickets(order.getGrantedTickets())
                .beforeTickets(order.getBeforeTickets())
                .afterTickets(order.getAfterTickets())
                .processedAt(order.getProcessedAt() == null ? null : order.getProcessedAt().atOffset(ZoneOffset.UTC))
                .createdAt(order.getCreatedAt().atOffset(ZoneOffset.UTC))
                .build();
    }
}

