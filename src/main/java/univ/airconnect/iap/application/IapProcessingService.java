package univ.airconnect.iap.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.iap.domain.GrantStatus;
import univ.airconnect.iap.domain.IapProductPolicy;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.dto.request.AndroidPurchaseVerifyRequest;
import univ.airconnect.iap.dto.request.AndroidPurchasesSyncRequest;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.dto.request.IosTransactionsSyncRequest;
import univ.airconnect.iap.dto.response.IapSyncItemResponse;
import univ.airconnect.iap.dto.response.IapSyncResponse;
import univ.airconnect.iap.dto.response.IapVerifyResponse;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.repository.IapOrderRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class IapProcessingService {

    private final StoreVerifierResolver storeVerifierResolver;
    private final IapOrderRepository iapOrderRepository;
    private final TicketGrantService ticketGrantService;

    public IapProcessingService(StoreVerifierResolver storeVerifierResolver,
                                IapOrderRepository iapOrderRepository,
                                TicketGrantService ticketGrantService) {
        this.storeVerifierResolver = storeVerifierResolver;
        this.iapOrderRepository = iapOrderRepository;
        this.ticketGrantService = ticketGrantService;
    }

    @Transactional
    public IapVerifyResponse verifyIos(Long userId, IosTransactionVerifyRequest request) {
        StoreVerificationResult result = storeVerifierResolver.resolve(IapStore.APPLE).verify(userId, request);
        return process(userId, result);
    }

    @Transactional
    public IapVerifyResponse verifyAndroid(Long userId, AndroidPurchaseVerifyRequest request) {
        StoreVerificationResult result = storeVerifierResolver.resolve(IapStore.GOOGLE).verify(userId, request);
        return process(userId, result);
    }

    @Transactional
    public IapSyncResponse syncIos(Long userId, IosTransactionsSyncRequest request) {
        List<IapSyncItemResponse> items = new ArrayList<>();
        int successCount = 0;
        for (IosTransactionsSyncRequest.IosSyncItem item : request.getTransactions()) {
            try {
                IosTransactionVerifyRequest req = new IosTransactionVerifyRequest(
                        item.getSignedTransactionInfo(),
                        item.getTransactionId(),
                        item.getAppAccountToken()
                );
                IapVerifyResponse response = verifyIos(userId, req);
                items.add(IapSyncItemResponse.builder().success(true).result(response).build());
                successCount++;
            } catch (IapException e) {
                items.add(IapSyncItemResponse.builder()
                        .success(false)
                        .errorCode(e.getErrorCode().getCode())
                        .message(e.getMessage())
                        .build());
            }
        }
        return IapSyncResponse.builder()
                .total(items.size())
                .successCount(successCount)
                .failureCount(items.size() - successCount)
                .results(items)
                .build();
    }

    @Transactional
    public IapSyncResponse syncAndroid(Long userId, AndroidPurchasesSyncRequest request) {
        List<IapSyncItemResponse> items = new ArrayList<>();
        int successCount = 0;
        for (AndroidPurchasesSyncRequest.AndroidSyncItem item : request.getPurchases()) {
            try {
                AndroidPurchaseVerifyRequest req = new AndroidPurchaseVerifyRequest(
                        item.getProductId(),
                        item.getPurchaseToken(),
                        item.getOrderId(),
                        item.getPackageName(),
                        null
                );
                IapVerifyResponse response = verifyAndroid(userId, req);
                items.add(IapSyncItemResponse.builder().success(true).result(response).build());
                successCount++;
            } catch (IapException e) {
                items.add(IapSyncItemResponse.builder()
                        .success(false)
                        .errorCode(e.getErrorCode().getCode())
                        .message(e.getMessage())
                        .build());
            }
        }
        return IapSyncResponse.builder()
                .total(items.size())
                .successCount(successCount)
                .failureCount(items.size() - successCount)
                .results(items)
                .build();
    }

    private IapVerifyResponse process(Long userId, StoreVerificationResult result) {
        IapProductPolicy productPolicy = IapProductPolicy.fromProductId(result.getProductId());
        if (productPolicy == null) {
            throw new IapException(IapErrorCode.IAP_INVALID_PRODUCT);
        }

        IapOrder existing = findExisting(result);
        if (existing != null && existing.getStatus() == univ.airconnect.iap.domain.IapOrderStatus.GRANTED) {
            return toAlreadyGranted(existing);
        }

        IapOrder order = existing;
        if (order == null) {
            order = createPendingOrder(userId, result);
        }

        if (!result.isValid()) {
            order.markRejected();
            return IapVerifyResponse.builder()
                    .transactionId(result.getTransactionId())
                    .purchaseToken(result.getPurchaseToken())
                    .orderId(result.getOrderId())
                    .productId(result.getProductId())
                    .grantStatus(GrantStatus.REJECTED)
                    .processedAt(order.getProcessedAt() == null ? null : order.getProcessedAt().atOffset(ZoneOffset.UTC))
                    .build();
        }

        order.markVerified();
        TicketGrantService.TicketGrantResult grant = ticketGrantService.grantTickets(order, productPolicy.getTickets());
        order.markGranted(productPolicy.getTickets(), grant.beforeTickets(), grant.afterTickets());

        log.info("IAP granted. userId={}, store={}, key={}, productId={}, tickets={}",
                userId, result.getStore(), order.idempotencyKey(), result.getProductId(), productPolicy.getTickets());

        return IapVerifyResponse.builder()
                .transactionId(order.getTransactionId())
                .purchaseToken(order.getPurchaseToken())
                .orderId(order.getOrderId())
                .productId(order.getProductId())
                .grantStatus(GrantStatus.GRANTED)
                .grantedTickets(order.getGrantedTickets())
                .beforeTickets(order.getBeforeTickets())
                .afterTickets(order.getAfterTickets())
                .ledgerId(grant.ledgerExternalId())
                .processedAt(order.getProcessedAt().atOffset(ZoneOffset.UTC))
                .build();
    }

    private IapOrder findExisting(StoreVerificationResult result) {
        if (result.getStore() == IapStore.APPLE) {
            return iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, result.getTransactionId()).orElse(null);
        }
        return iapOrderRepository.findByStoreAndPurchaseToken(IapStore.GOOGLE, result.getPurchaseToken()).orElse(null);
    }

    private IapOrder createPendingOrder(Long userId, StoreVerificationResult result) {
        try {
            return iapOrderRepository.save(IapOrder.createPending(
                    userId,
                    result.getStore(),
                    result.getProductId(),
                    result.getTransactionId(),
                    result.getOriginalTransactionId(),
                    result.getPurchaseToken(),
                    result.getOrderId(),
                    result.getAppAccountToken(),
                    result.getEnvironment(),
                    result.getVerificationHash(),
                    result.getRawPayloadMasked()
            ));
        } catch (DataIntegrityViolationException e) {
            IapOrder existing = findExisting(result);
            if (existing != null) {
                return existing;
            }
            throw new IapException(IapErrorCode.IAP_DUPLICATE_REQUEST);
        }
    }

    private IapVerifyResponse toAlreadyGranted(IapOrder order) {
        return IapVerifyResponse.builder()
                .transactionId(order.getTransactionId())
                .purchaseToken(order.getPurchaseToken())
                .orderId(order.getOrderId())
                .productId(order.getProductId())
                .grantStatus(GrantStatus.ALREADY_GRANTED)
                .grantedTickets(order.getGrantedTickets())
                .beforeTickets(order.getBeforeTickets())
                .afterTickets(order.getAfterTickets())
                .ledgerId("TICKET_LEDGER_REF_" + order.getId())
                .processedAt(order.getProcessedAt() == null ? null : order.getProcessedAt().atOffset(ZoneOffset.UTC))
                .build();
    }

}


