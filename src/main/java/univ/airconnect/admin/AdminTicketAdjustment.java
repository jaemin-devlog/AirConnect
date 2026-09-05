package univ.airconnect.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Durable receipt. Deliberately has no User relationship, cascade or retention worker. */
@Entity
@Table(name = "admin_ticket_adjustments", uniqueConstraints =
        @UniqueConstraint(name = "uk_admin_ticket_adjustment_operation", columnNames = "operation_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminTicketAdjustment {
    public enum Status { PROCESSING, COMPLETED, REJECTED }

    // A generated identity makes saveAndFlush INSERT, not merge an assigned operation UUID.
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "operation_id", nullable = false, length = 36, updatable = false)
    private String operationId;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(nullable = false, updatable = false)
    private int amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
    private Status status;
    @Column(name = "before_tickets")
    private Integer beforeTickets;
    @Column(name = "after_tickets")
    private Integer afterTickets;
    @Column(name = "ledger_id")
    private Long ledgerId;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "rejection_code", length = 60)
    private String rejectionCode;
    @Column(name = "rejection_message", length = 300)
    private String rejectionMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AdminTicketAdjustment claim(String operationId, String requestHash, Long actorUserId,
                                             Long userId, int amount, LocalDateTime createdAt) {
        var operation = new AdminTicketAdjustment();
        operation.operationId = operationId;
        operation.requestHash = requestHash;
        operation.actorUserId = actorUserId;
        operation.userId = userId;
        operation.amount = amount;
        operation.status = Status.PROCESSING;
        operation.createdAt = createdAt;
        return operation;
    }

    public void complete(int before, int after, Long ledgerId, LocalDateTime completedAt) {
        requireProcessing();
        this.beforeTickets = before;
        this.afterTickets = after;
        this.ledgerId = ledgerId;
        this.completedAt = completedAt;
        this.status = Status.COMPLETED;
    }

    public void reject(Integer unchangedBalance, String code, String message, LocalDateTime completedAt) {
        requireProcessing();
        this.beforeTickets = unchangedBalance;
        this.afterTickets = unchangedBalance;
        this.rejectionCode = code;
        this.rejectionMessage = message;
        this.completedAt = completedAt;
        this.status = Status.REJECTED;
    }

    private void requireProcessing() {
        if (status != Status.PROCESSING) throw new IllegalStateException("Ticket adjustment already finalized");
    }
}
