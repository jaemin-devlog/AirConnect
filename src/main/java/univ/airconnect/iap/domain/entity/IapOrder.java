package univ.airconnect.iap.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapOrderStatus;
import univ.airconnect.iap.domain.IapStore;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "iap_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_iap_orders_store_transaction", columnNames = {"store", "transaction_id"}),
                @UniqueConstraint(name = "uk_iap_orders_store_purchase_token", columnNames = {"store", "purchase_token"})
        },
        indexes = {
                @Index(name = "idx_iap_orders_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_iap_orders_store_product", columnList = "store, product_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IapOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IapStore store;

    @Column(name = "product_id", nullable = false, length = 120)
    private String productId;

    @Column(name = "transaction_id", length = 80)
    private String transactionId;

    @Column(name = "original_transaction_id", length = 80)
    private String originalTransactionId;

    @Column(name = "purchase_token", length = 512)
    private String purchaseToken;

    @Column(name = "order_id", length = 120)
    private String orderId;

    @Column(name = "app_account_token", length = 120)
    private String appAccountToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IapEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IapOrderStatus status;

    @Column(name = "granted_tickets")
    private Integer grantedTickets;

    @Column(name = "before_tickets")
    private Integer beforeTickets;

    @Column(name = "after_tickets")
    private Integer afterTickets;

    @Column(name = "verification_hash", nullable = false, length = 100)
    private String verificationHash;

    @Column(name = "raw_payload_masked", nullable = false, length = 1200)
    private String rawPayloadMasked;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private IapOrder(Long userId,
                     IapStore store,
                     String productId,
                     String transactionId,
                     String originalTransactionId,
                     String purchaseToken,
                     String orderId,
                     String appAccountToken,
                     IapEnvironment environment,
                     IapOrderStatus status,
                     String verificationHash,
                     String rawPayloadMasked) {
        this.userId = userId;
        this.store = store;
        this.productId = productId;
        this.transactionId = transactionId;
        this.originalTransactionId = originalTransactionId;
        this.purchaseToken = purchaseToken;
        this.orderId = orderId;
        this.appAccountToken = appAccountToken;
        this.environment = environment;
        this.status = status;
        this.verificationHash = verificationHash;
        this.rawPayloadMasked = rawPayloadMasked;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static IapOrder createPending(Long userId,
                                         IapStore store,
                                         String productId,
                                         String transactionId,
                                         String originalTransactionId,
                                         String purchaseToken,
                                         String orderId,
                                         String appAccountToken,
                                         IapEnvironment environment,
                                         String verificationHash,
                                         String rawPayloadMasked) {
        return IapOrder.builder()
                .userId(userId)
                .store(store)
                .productId(productId)
                .transactionId(transactionId)
                .originalTransactionId(originalTransactionId)
                .purchaseToken(purchaseToken)
                .orderId(orderId)
                .appAccountToken(appAccountToken)
                .environment(environment)
                .status(IapOrderStatus.PENDING)
                .verificationHash(verificationHash)
                .rawPayloadMasked(rawPayloadMasked)
                .build();
    }

    public void markVerified() {
        this.status = IapOrderStatus.VERIFIED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markGranted(int grantedTickets, int beforeTickets, int afterTickets) {
        this.status = IapOrderStatus.GRANTED;
        this.grantedTickets = grantedTickets;
        this.beforeTickets = beforeTickets;
        this.afterTickets = afterTickets;
        this.processedAt = LocalDateTime.now();
        this.updatedAt = this.processedAt;
    }

    public void markRejected() {
        this.status = IapOrderStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.updatedAt = this.processedAt;
    }

    public String idempotencyKey() {
        if (store == IapStore.APPLE) {
            return transactionId;
        }
        return purchaseToken;
    }
}

