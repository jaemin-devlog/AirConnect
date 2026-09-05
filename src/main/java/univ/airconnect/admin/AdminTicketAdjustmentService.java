package univ.airconnect.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * This wrapper is intentionally not transactional: a duplicate INSERT must finish rolling
 * back before the winning, committed receipt is read in a fresh transaction. No pending
 * claim is committed separately, and failed infrastructure writes never become REJECTED.
 */
@Service
public class AdminTicketAdjustmentService {
    private final AdminTicketAdjustmentRepository operations;
    private final UserRepository users;
    private final TicketLedgerRepository ledgers;
    private final NotificationService notifications;
    private final AdminAuditLogService audits;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public AdminTicketAdjustmentService(AdminTicketAdjustmentRepository operations, UserRepository users,
                                        TicketLedgerRepository ledgers, NotificationService notifications,
                                        AdminAuditLogService audits, ObjectMapper objectMapper,
                                        PlatformTransactionManager transactionManager) {
        this.operations = operations;
        this.users = users;
        this.ledgers = ledgers;
        this.notifications = notifications;
        this.audits = audits;
        this.objectMapper = objectMapper;
        this.write = new TransactionTemplate(transactionManager);
        this.write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.read = new TransactionTemplate(transactionManager);
        this.read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.read.setReadOnly(true);
    }

    public AdminDtos.TicketAdjustmentResult adjust(Long adminId, AdminRequests.TicketAdjustmentRequest request) {
        validate(request);
        String operationId = normalizeOperationId(request.operationId());
        String requestHash = requestHash(request);
        try {
            return Objects.requireNonNull(write.execute(tx -> execute(adminId, operationId, requestHash, request)));
        } catch (DataIntegrityViolationException failure) {
            // Do not reuse the failed EntityManager or treat every constraint failure as a
            // duplicate. Only an actual committed receipt can establish the outcome.
            return Objects.requireNonNull(read.execute(tx -> {
                requireAdmin(adminId);
                AdminTicketAdjustment winner = operations.findByOperationId(operationId).orElseThrow(() -> failure);
                if (!winner.getRequestHash().equals(requestHash)) {
                    throw new BusinessException(ErrorCode.TICKET_ADJUSTMENT_CONFLICT);
                }
                return result(winner);
            }));
        }
    }

    public AdminDtos.TicketAdjustmentResult get(Long adminId, String suppliedOperationId) {
        String operationId = normalizeOperationId(suppliedOperationId);
        return Objects.requireNonNull(read.execute(tx -> {
            requireAdmin(adminId);
            return result(operations.findByOperationId(operationId).orElseThrow(() ->
                    new BusinessException(ErrorCode.NOT_FOUND,
                            "확정된 작업 결과를 찾지 못했습니다. 아직 처리 중일 수 있습니다.")));
        }));
    }

    private AdminDtos.TicketAdjustmentResult execute(Long adminId, String operationId, String requestHash,
                                                   AdminRequests.TicketAdjustmentRequest request) {
        User actor = requireAdmin(adminId);
        // INSERT and its unique check happen before any balance, notification or audit write.
        AdminTicketAdjustment operation = operations.saveAndFlush(AdminTicketAdjustment.claim(
                operationId, requestHash, adminId, request.userId(), request.amount(), now()));
        User user = users.findByIdForTicketUpdate(request.userId()).orElse(null);
        if (user == null) {
            operation.reject(null, ErrorCode.NOT_FOUND.getCode(), "사용자를 찾을 수 없습니다.", now());
            return finish(operation);
        }

        int before = user.getTickets();
        int after;
        try {
            after = Math.addExact(before, request.amount());
        } catch (ArithmeticException overflow) {
            operation.reject(before, ErrorCode.INVALID_REQUEST.getCode(),
                    "변경 후 티켓 잔액이 허용 범위를 벗어납니다.", now());
            return finish(operation);
        }
        // Preserve the original policy: credits toward an existing negative refund balance
        // are allowed, while debits may not exceed the current available balance.
        if (request.amount() < 0 && before < Math.abs(request.amount())) {
            operation.reject(before, ErrorCode.INVALID_REQUEST.getCode(), "티켓 잔액이 부족합니다.", now());
            return finish(operation);
        }

        user.adjustTickets(request.amount());
        TicketLedger history = ledgers.save(TicketLedger.adjustByAdmin(user.getId(), request.amount(), before,
                after, "ADMIN:" + request.reason(), "admin-adjustment:" + operationId));
        sendAnnouncement(actor, operationId, request, before, after);
        audits.recordTicketAdjustment(adminId, user.getId(), operationId, request.amount(), before, after, request.reason());
        operation.complete(before, after, history.getId(), now());
        return finish(operation);
    }

    private AdminDtos.TicketAdjustmentResult finish(AdminTicketAdjustment operation) {
        operations.flush();
        return result(operation);
    }

    private AdminDtos.TicketAdjustmentResult result(AdminTicketAdjustment operation) {
        if (operation.getStatus() == AdminTicketAdjustment.Status.PROCESSING || operation.getCompletedAt() == null) {
            // A provisional claim must never be visible as a completed or rejected result.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new AdminDtos.TicketAdjustmentResult(operation.getOperationId(), operation.getStatus().name(),
                operation.getUserId(), operation.getAmount(), operation.getBeforeTickets(), operation.getAfterTickets(),
                operation.getLedgerId(), operation.getCompletedAt(), operation.getRejectionCode(), operation.getRejectionMessage());
    }

    private User requireAdmin(Long adminId) {
        if (adminId == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        User admin = users.findById(adminId).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (admin.getStatus() != UserStatus.ACTIVE || admin.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return admin;
    }

    private void validate(AdminRequests.TicketAdjustmentRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "대상 회원을 확인해 주세요.");
        }
        if (request.reason() == null || request.reason().isBlank()
                || request.reason().length() > AdminRequests.MAX_TICKET_ADJUSTMENT_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "티켓 조정 사유는 1~54자로 입력해 주세요.");
        }
        if (request.amount() == 0 || request.amount() == Integer.MIN_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "티켓 변경량은 0이 아닌 유효한 정수여야 합니다.");
        }
    }

    private String normalizeOperationId(String supplied) {
        if (supplied == null || !supplied.trim().matches(AdminRequests.OPERATION_UUID_PATTERN)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "유효한 작업번호가 필요합니다.");
        }
        return UUID.fromString(supplied.trim()).toString();
    }

    private String requestHash(AdminRequests.TicketAdjustmentRequest request) {
        // Versioned and unambiguous: the first two fields are numeric and the remaining
        // text is the normalized reason. Actor is intentionally excluded: any active
        // administrator may retrieve or replay the same request under the same number.
        String canonical = "admin-ticket-adjustment:v1\n" + request.userId() + "\n" + request.amount() + "\n" + request.reason();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private void sendAnnouncement(User actor, String operationId, AdminRequests.TicketAdjustmentRequest request,
                                  int before, int after) {
        String senderName = actor.getName() != null && !actor.getName().isBlank() ? actor.getName().trim()
                : actor.getNickname() != null && !actor.getNickname().isBlank() ? actor.getNickname().trim() : "운영팀";
        String message = request.amount() > 0 ? "티켓 " + request.amount() + "개가 지급되었습니다."
                : "티켓 " + Math.abs(request.amount()) + "개가 차감되었습니다.";
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "kind", "ADMIN_TICKET_ADJUSTMENT", "operationId", operationId,
                    "amount", request.amount(), "reason", request.reason(),
                    "beforeTickets", before, "afterTickets", after,
                    "senderName", senderName, "senderUserId", actor.getId()));
        } catch (JsonProcessingException failure) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "공지 payload 생성에 실패했습니다.");
        }
        notifications.createAndEnqueue(new NotificationService.CreateCommand(request.userId(),
                NotificationType.SYSTEM_ANNOUNCEMENT, senderName, message + " 사유: " + request.reason() + ".",
                null, actor.getId(), null, payload, "admin-ticket-adjustment:" + operationId));
    }

    private LocalDateTime now() {
        // Match DATETIME(6) precision so the first response equals all later receipt reads.
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
