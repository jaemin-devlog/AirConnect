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
        log.info("IAP verifyIos started. userId={}, transactionId={}", userId, request.getTransactionId());
        StoreVerificationResult result = storeVerifierResolver.resolve(IapStore.APPLE).verify(userId, request);
        log.info("IAP verifyIos store verification succeeded. userId={}, store={}, transactionId={}, productId={}",
                userId, result.getStore(), result.getTransactionId(), result.getProductId());
        return process(userId, result);
    }

    @Transactional
    public IapVerifyResponse verifyAndroid(Long userId, AndroidPurchaseVerifyRequest request) {
        log.info("IAP verifyAndroid started. userId={}, orderId={}", userId, request.getOrderId());
        StoreVerificationResult result = storeVerifierResolver.resolve(IapStore.GOOGLE).verify(userId, request);
        log.info("IAP verifyAndroid store verification succeeded. userId={}, store={}, purchaseTokenExists={}, productId={}",
                userId, result.getStore(), result.getPurchaseToken() != null, result.getProductId());
        return process(userId, result);
    }

    @Transactional
    public IapSyncResponse syncIos(Long userId, IosTransactionsSyncRequest request) {
        log.info("IAP syncIos started. userId={}, itemCount={}", userId, request.getTransactions().size());
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
                log.warn("IAP syncIos item failed. userId={}, transactionId={}, errorCode={}, message={}",
                        userId, item.getTransactionId(), e.getErrorCode().getCode(), e.getMessage());
                items.add(IapSyncItemResponse.builder()
                        .success(false)
                        .errorCode(e.getErrorCode().getCode())
                        .message(e.getMessage())
                        .build());
            }
        }
        log.info("IAP syncIos completed. userId={}, total={}, success={}, failure={}",
                userId, items.size(), successCount, items.size() - successCount);
        return IapSyncResponse.builder()
                .total(items.size())
                .successCount(successCount)
                .failureCount(items.size() - successCount)
                .results(items)
                .build();
    }

    @Transactional
    public IapSyncResponse syncAndroid(Long userId, AndroidPurchasesSyncRequest request) {
        log.info("IAP syncAndroid started. userId={}, itemCount={}", userId, request.getPurchases().size());
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
                log.warn("IAP syncAndroid item failed. userId={}, orderId={}, errorCode={}, message={}",
                        userId, item.getOrderId(), e.getErrorCode().getCode(), e.getMessage());
                items.add(IapSyncItemResponse.builder()
                        .success(false)
                        .errorCode(e.getErrorCode().getCode())
                        .message(e.getMessage())
                        .build());
            }
        }
        log.info("IAP syncAndroid completed. userId={}, total={}, success={}, failure={}",
                userId, items.size(), successCount, items.size() - successCount);
        return IapSyncResponse.builder()
                .total(items.size())
                .successCount(successCount)
                .failureCount(items.size() - successCount)
                .results(items)
                .build();
    }

    private IapVerifyResponse process(Long userId, StoreVerificationResult result) {
        log.info("IAP process started. userId={}, store={}, key={}, productId={}, valid={}",
                userId, result.getStore(), keyOf(result), result.getProductId(), result.isValid());
        IapProductPolicy productPolicy = IapProductPolicy.fromProductId(result.getProductId());
        if (productPolicy == null) {
            log.warn("IAP process invalid product. userId={}, store={}, productId={}",
                    userId, result.getStore(), result.getProductId());
            throw new IapException(IapErrorCode.IAP_INVALID_PRODUCT);
        }

        IapOrder existing = findExisting(result);
        if (existing != null && existing.getStatus() == univ.airconnect.iap.domain.IapOrderStatus.GRANTED) {
            log.info("IAP process already granted. userId={}, store={}, orderId={}, key={}",
                    userId, result.getStore(), existing.getId(), existing.idempotencyKey());
            return toAlreadyGranted(existing);
        }

        IapOrder order = existing;
        if (order == null) {
            order = createPendingOrder(userId, result);
        }

        if (!result.isValid()) {
            order.markRejected();
            log.warn("IAP process rejected. userId={}, store={}, orderId={}, key={}",
                    userId, result.getStore(), order.getId(), order.idempotencyKey());
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
        log.info("IAP process marked verified. userId={}, store={}, orderId={}, key={}",
                userId, result.getStore(), order.getId(), order.idempotencyKey());
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
            IapOrder found = iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, result.getTransactionId()).orElse(null);
            if (found != null) {
                log.info("IAP existing order found. store=APPLE, orderId={}, transactionId={}", found.getId(), result.getTransactionId());
            }
            return found;
        }
        IapOrder found = iapOrderRepository.findByStoreAndPurchaseToken(IapStore.GOOGLE, result.getPurchaseToken()).orElse(null);
        if (found != null) {
            log.info("IAP existing order found. store=GOOGLE, orderId={}, purchaseTokenExists={}", found.getId(), result.getPurchaseToken() != null);
        }
        return found;
    }

    private IapOrder createPendingOrder(Long userId, StoreVerificationResult result) {
        try {
            IapOrder created = iapOrderRepository.save(IapOrder.createPending(
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
            log.info("IAP pending order created. userId={}, store={}, orderId={}, key={}",
                    userId, result.getStore(), created.getId(), created.idempotencyKey());
            return created;
        } catch (DataIntegrityViolationException e) {
            log.warn("IAP pending order creation hit unique constraint. userId={}, store={}, key={}",
                    userId, result.getStore(), keyOf(result));
            IapOrder existing = findExisting(result);
            if (existing != null) {
                return existing;
            }
            throw new IapException(IapErrorCode.IAP_DUPLICATE_REQUEST);
        }
    }

    private IapVerifyResponse toAlreadyGranted(IapOrder order) {
        log.info("IAP already granted response generated. orderId={}, store={}, key={}",
                order.getId(), order.getStore(), order.idempotencyKey());
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

    private String keyOf(StoreVerificationResult result) {
        if (result.getStore() == IapStore.APPLE) {
            return result.getTransactionId();
        }
        return result.getPurchaseToken() != null ? "PRESENT" : "MISSING";
    }

}


