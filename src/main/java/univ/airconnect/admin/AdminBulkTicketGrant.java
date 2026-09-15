package univ.airconnect.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Durable receipt for one logical all-member ticket grant. */
@Entity
@Table(name = "admin_bulk_ticket_grants", uniqueConstraints =
        @UniqueConstraint(name = "uk_admin_bulk_ticket_grant_operation", columnNames = "operation_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminBulkTicketGrant {
    public enum Status { PROCESSING, COMPLETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "operation_id", nullable = false, length = 36, updatable = false)
    private String operationId;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;
    @Column(nullable = false, updatable = false)
    private int amount;
    @Column(nullable = false, length = 500, updatable = false)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
    private Status status;
    @Column(name = "target_count")
    private Integer targetCount;
    @Column(name = "granted_count")
    private Integer grantedCount;
    @Column(name = "total_granted_tickets")
    private Long totalGrantedTickets;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static AdminBulkTicketGrant claim(String operationId, String requestHash, Long actorUserId,
                                             int amount, String message, LocalDateTime createdAt) {
        AdminBulkTicketGrant operation = new AdminBulkTicketGrant();
        operation.operationId = operationId;
        operation.requestHash = requestHash;
        operation.actorUserId = actorUserId;
        operation.amount = amount;
        operation.message = message;
        operation.status = Status.PROCESSING;
        operation.createdAt = createdAt;
        return operation;
    }

    public void complete(int targetCount, int grantedCount, long totalGrantedTickets, LocalDateTime completedAt) {
        if (status != Status.PROCESSING) throw new IllegalStateException("Bulk ticket grant already finalized");
        this.targetCount = targetCount;
        this.grantedCount = grantedCount;
        this.totalGrantedTickets = totalGrantedTickets;
        this.completedAt = completedAt;
        this.status = Status.COMPLETED;
    }
}
