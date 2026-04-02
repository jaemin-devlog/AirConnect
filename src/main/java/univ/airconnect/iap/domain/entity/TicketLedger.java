package univ.airconnect.iap.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.iap.domain.LedgerRefType;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ticket_ledger",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ticket_ledger_ref", columnNames = {"ref_type", "ref_id"})
        },
        indexes = {
                @Index(name = "idx_ticket_ledger_user_created", columnList = "user_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "change_amount", nullable = false)
    private Integer changeAmount;

    @Column(name = "before_amount", nullable = false)
    private Integer beforeAmount;

    @Column(name = "after_amount", nullable = false)
    private Integer afterAmount;

    @Column(nullable = false, length = 60)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 30)
    private LedgerRefType refType;

    @Column(name = "ref_id", nullable = false, length = 120)
    private String refId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private TicketLedger(Long userId,
                         Integer changeAmount,
                         Integer beforeAmount,
                         Integer afterAmount,
                         String reason,
                         LedgerRefType refType,
                         String refId) {
        this.userId = userId;
        this.changeAmount = changeAmount;
        this.beforeAmount = beforeAmount;
        this.afterAmount = afterAmount;
        this.reason = reason;
        this.refType = refType;
        this.refId = refId;
        this.createdAt = LocalDateTime.now();
    }

    public static TicketLedger grantForIap(Long userId,
                                           int amount,
                                           int beforeAmount,
                                           int afterAmount,
                                           String refId) {
        return TicketLedger.builder()
                .userId(userId)
                .changeAmount(amount)
                .beforeAmount(beforeAmount)
                .afterAmount(afterAmount)
                .reason("IAP_PURCHASE")
                .refType(LedgerRefType.IAP_ORDER)
                .refId(refId)
                .build();
    }

    public static TicketLedger grantForAdReward(Long userId,
                                                int amount,
                                                int beforeAmount,
                                                int afterAmount,
                                                String refId) {
        return TicketLedger.builder()
                .userId(userId)
                .changeAmount(amount)
                .beforeAmount(beforeAmount)
                .afterAmount(afterAmount)
                .reason("AD_REWARD")
                .refType(LedgerRefType.AD_REWARD_SESSION)
                .refId(refId)
                .build();
    }

    public String ledgerExternalId() {
        return "TICKET_LEDGER_" + id;
    }
}

