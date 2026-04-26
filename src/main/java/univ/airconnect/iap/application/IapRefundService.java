package univ.airconnect.iap.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.iap.domain.IapOrderStatus;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class IapRefundService {

    private final IapOrderRepository iapOrderRepository;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final UserRepository userRepository;

    @Transactional
    public RefundResult refundAppleTransaction(String transactionId, String source) {
        if (transactionId == null || transactionId.isBlank()) {
            return RefundResult.notHandled("missing_transaction_id");
        }
        IapOrder order = iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, transactionId).orElse(null);
        return refundOrder(order, source);
    }

    @Transactional
    public RefundResult revokeGooglePurchase(String purchaseToken, String source) {
        if (purchaseToken == null || purchaseToken.isBlank()) {
            return RefundResult.notHandled("missing_purchase_token");
        }
        IapOrder order = iapOrderRepository.findByStoreAndPurchaseToken(IapStore.GOOGLE, purchaseToken).orElse(null);
        if (order == null) {
            return RefundResult.notHandled("order_not_found");
        }
        IapOrder locked = lockOrder(order.getId());
        if (locked.getStatus() == IapOrderStatus.REFUNDED || locked.getStatus() == IapOrderStatus.REVOKED) {
            return RefundResult.alreadyHandled(locked.getStatus().name().toLowerCase());
        }
        if (locked.getStatus() == IapOrderStatus.GRANTED) {
            return refundOrder(locked, source);
        }
        locked.markRevoked();
        log.info("IAP revoke handled without ticket reversal. orderId={}, store={}, source={}",
                locked.getId(), locked.getStore(), source);
        return RefundResult.revokedResult();
    }

    @Transactional
    public RefundResult refundGooglePurchase(String purchaseToken, String source) {
        if (purchaseToken == null || purchaseToken.isBlank()) {
            return RefundResult.notHandled("missing_purchase_token");
        }
        IapOrder order = iapOrderRepository.findByStoreAndPurchaseToken(IapStore.GOOGLE, purchaseToken).orElse(null);
        return refundOrder(order, source);
    }

    @Transactional
    public RefundResult refundGrantedOrder(IapOrder order, String source) {
        return refundOrder(order, source);
    }

    private RefundResult refundOrder(IapOrder order, String source) {
        if (order == null) {
            return RefundResult.notHandled("order_not_found");
        }

        IapOrder locked = lockOrder(order.getId());
        if (locked.getStatus() == IapOrderStatus.REFUNDED) {
            return RefundResult.alreadyHandled("already_refunded");
        }
        if (locked.getStatus() == IapOrderStatus.REVOKED) {
            return RefundResult.alreadyHandled("already_revoked");
        }
        if (locked.getStatus() != IapOrderStatus.GRANTED) {
            locked.markRevoked();
            log.info("IAP refund request mapped to revoke because order not granted. orderId={}, status={}, source={}",
                    locked.getId(), locked.getStatus(), source);
            return RefundResult.revokedResult();
        }

        String refundRefId = String.valueOf(locked.getId());
        TicketLedger existingRefund = ticketLedgerRepository.findByRefTypeAndRefId(LedgerRefType.IAP_REFUND, refundRefId)
                .orElse(null);
        if (existingRefund != null) {
            locked.markRefunded();
            return RefundResult.alreadyHandled(existingRefund.ledgerExternalId());
        }

        User user = userRepository.findByIdForUpdate(locked.getUserId())
                .orElseThrow(() -> new IapException(IapErrorCode.IAP_UNAUTHORIZED));
        int refundAmount = locked.getGrantedTickets() == null ? 0 : locked.getGrantedTickets();
        int before = user.getTickets();
        user.deductTicketsAllowNegative(refundAmount);
        int after = user.getTickets();

        try {
            TicketLedger refundLedger = ticketLedgerRepository.save(
                    TicketLedger.refundForIap(user.getId(), refundAmount, before, after, refundRefId)
            );
            locked.markRefunded();
            log.info("IAP refund processed. orderId={}, userId={}, refundAmount={}, ledgerId={}, source={}",
                    locked.getId(), user.getId(), refundAmount, refundLedger.ledgerExternalId(), source);
            return RefundResult.refunded(refundLedger.ledgerExternalId(), before, after);
        } catch (DataIntegrityViolationException e) {
            TicketLedger recovered = ticketLedgerRepository.findByRefTypeAndRefId(LedgerRefType.IAP_REFUND, refundRefId)
                    .orElseThrow(() -> new IapException(IapErrorCode.IAP_DUPLICATE_REQUEST));
            locked.markRefunded();
            log.info("IAP refund recovered after unique conflict. orderId={}, ledgerId={}, source={}",
                    locked.getId(), recovered.ledgerExternalId(), source);
            return RefundResult.alreadyHandled(recovered.ledgerExternalId());
        }
    }

    private IapOrder lockOrder(Long orderId) {
        return iapOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IapException(IapErrorCode.IAP_ORDER_NOT_FOUND));
    }

    public record RefundResult(boolean handled,
                               boolean refunded,
                               boolean revoked,
                               String reference,
                               Integer beforeTickets,
                               Integer afterTickets,
                               String reason) {
        public static RefundResult refunded(String reference, int beforeTickets, int afterTickets) {
            return new RefundResult(true, true, false, reference, beforeTickets, afterTickets, null);
        }

        public static RefundResult revokedResult() {
            return new RefundResult(true, false, true, null, null, null, null);
        }

        public static RefundResult alreadyHandled(String reference) {
            return new RefundResult(true, false, false, reference, null, null, null);
        }

        public static RefundResult notHandled(String reason) {
            return new RefundResult(false, false, false, null, null, null, reason);
        }
    }
}
