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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminBulkTicketGrantService {
    static final String TARGET_DESCRIPTION = "현재 정상 상태인 일반 회원(관리자·탈퇴·정지·제한 회원 제외)";

    private final AdminBulkTicketGrantRepository operations;
    private final UserRepository users;
    private final TicketLedgerRepository ledgers;
    private final NotificationService notifications;
    private final AdminAuditLogService audits;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public AdminBulkTicketGrantService(AdminBulkTicketGrantRepository operations, UserRepository users,
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

    public AdminDtos.BulkTicketGrantPreview preview(Long adminId) {
        return Objects.requireNonNull(read.execute(tx -> {
            requireAdmin(adminId);
            return new AdminDtos.BulkTicketGrantPreview(
                    users.countByStatusAndRole(UserStatus.ACTIVE, UserRole.USER),
                    TARGET_DESCRIPTION,
                    now());
        }));
    }

    public AdminDtos.BulkTicketGrantResult grant(Long adminId, AdminRequests.BulkTicketGrantRequest request) {
        validate(request);
        String operationId = normalizeOperationId(request.operationId());
        String requestHash = requestHash(request);
        try {
            return Objects.requireNonNull(write.execute(tx -> execute(adminId, operationId, requestHash, request)));
        } catch (DataIntegrityViolationException failure) {
            return Objects.requireNonNull(read.execute(tx -> {
                requireAdmin(adminId);
                AdminBulkTicketGrant winner = operations.findByOperationId(operationId).orElseThrow(() -> failure);
                if (!winner.getRequestHash().equals(requestHash)) {
                    throw new BusinessException(ErrorCode.BULK_TICKET_GRANT_CONFLICT);
                }
                return result(winner);
            }));
        }
    }

    public AdminDtos.BulkTicketGrantResult get(Long adminId, String suppliedOperationId) {
        String operationId = normalizeOperationId(suppliedOperationId);
        return Objects.requireNonNull(read.execute(tx -> {
            requireAdmin(adminId);
            return result(operations.findByOperationId(operationId).orElseThrow(() ->
                    new BusinessException(ErrorCode.NOT_FOUND,
                            "확정된 전체 지급 결과를 찾지 못했습니다. 아직 처리 중일 수 있습니다.")));
        }));
    }

    private AdminDtos.BulkTicketGrantResult execute(Long adminId, String operationId, String requestHash,
                                                    AdminRequests.BulkTicketGrantRequest request) {
        User actor = requireAdmin(adminId);
        AdminBulkTicketGrant operation = operations.saveAndFlush(AdminBulkTicketGrant.claim(
                operationId, requestHash, adminId, request.amount(), request.message(), now()));

        List<User> recipients = users.findActiveRegularUsersForBulkTicketUpdate();
        String payload = payload(operationId, request.amount(), actor.getId());
        for (User user : recipients) {
            int before = user.getTickets();
            try {
                Math.addExact(before, request.amount());
            } catch (ArithmeticException overflow) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "회원 #" + user.getId() + "의 지급 후 티켓 잔액이 허용 범위를 벗어납니다.");
            }
            user.addTickets(request.amount());
            ledgers.save(TicketLedger.grantByAdminBulk(
                    user.getId(), request.amount(), before, user.getTickets(), operationId));
            notifications.createAndEnqueue(new NotificationService.CreateCommand(
                    user.getId(), NotificationType.SYSTEM_ANNOUNCEMENT,
                    "AirConnect 티켓 지급", request.message(), null, actor.getId(), null,
                    payload, "admin-bulk-grant:" + operationId + ":user:" + user.getId()));
        }

        int count = recipients.size();
        long total = Math.multiplyExact((long) request.amount(), count);
        audits.recordBulkTicketGrant(adminId, operationId, request.amount(), count, total, request.message());
        operation.complete(count, count, total, now());
        operations.flush();
        return result(operation);
    }

    private User requireAdmin(Long adminId) {
        if (adminId == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        User admin = users.findById(adminId).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (admin.getStatus() != UserStatus.ACTIVE || admin.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return admin;
    }

    private void validate(AdminRequests.BulkTicketGrantRequest request) {
        if (request == null || request.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지급 수량은 1개 이상이어야 합니다.");
        }
        if (request.message() == null || request.message().isBlank()
                || request.message().length() > AdminRequests.MAX_BULK_TICKET_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "회원 안내 문구는 1~500자로 입력해 주세요.");
        }
    }

    private String normalizeOperationId(String supplied) {
        if (supplied == null || !supplied.trim().matches(AdminRequests.OPERATION_UUID_PATTERN)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "유효한 작업번호가 필요합니다.");
        }
        return UUID.fromString(supplied.trim()).toString();
    }

    private String requestHash(AdminRequests.BulkTicketGrantRequest request) {
        String canonical = "admin-bulk-ticket-grant:v1\n" + request.amount() + "\n" + request.message();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private String payload(String operationId, int amount, Long actorId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "kind", "ADMIN_BULK_TICKET_GRANT",
                    "operationId", operationId,
                    "amount", amount,
                    "senderUserId", actorId));
        } catch (JsonProcessingException failure) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "알림 payload 생성에 실패했습니다.");
        }
    }

    private AdminDtos.BulkTicketGrantResult result(AdminBulkTicketGrant operation) {
        if (operation.getStatus() != AdminBulkTicketGrant.Status.COMPLETED || operation.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new AdminDtos.BulkTicketGrantResult(
                operation.getOperationId(), operation.getStatus().name(), operation.getAmount(),
                operation.getMessage(), operation.getTargetCount(), operation.getGrantedCount(),
                operation.getTotalGrantedTickets(), operation.getCompletedAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
