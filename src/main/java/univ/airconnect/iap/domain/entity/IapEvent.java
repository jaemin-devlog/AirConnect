package univ.airconnect.iap.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.iap.domain.IapStore;

import java.time.LocalDateTime;

@Entity
@Table(name = "iap_events", indexes = {
        @Index(name = "idx_iap_events_store_created", columnList = "store, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IapEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IapStore store;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "transaction_id", length = 120)
    private String transactionId;

    @Column(name = "purchase_token", length = 512)
    private String purchaseToken;

    @Column(name = "payload_hash", nullable = false, length = 100)
    private String payloadHash;

    @Column(name = "payload_masked", nullable = false, length = 1200)
    private String payloadMasked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private IapEvent(IapStore store,
                     String eventType,
                     String transactionId,
                     String purchaseToken,
                     String payloadHash,
                     String payloadMasked) {
        this.store = store;
        this.eventType = eventType;
        this.transactionId = transactionId;
        this.purchaseToken = purchaseToken;
        this.payloadHash = payloadHash;
        this.payloadMasked = payloadMasked;
        this.createdAt = LocalDateTime.now();
    }

    public static IapEvent create(IapStore store,
                                  String eventType,
                                  String transactionId,
                                  String purchaseToken,
                                  String payloadHash,
                                  String payloadMasked) {
        return IapEvent.builder()
                .store(store)
                .eventType(eventType)
                .transactionId(transactionId)
                .purchaseToken(purchaseToken)
                .payloadHash(payloadHash)
                .payloadMasked(payloadMasked)
                .build();
    }
}

