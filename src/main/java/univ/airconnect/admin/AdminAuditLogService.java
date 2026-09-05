package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

import java.util.Map;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_STATS_DAYS = 30;
    private static final int MAX_STATS_DAYS = 365;
    private static final int STATS_LIMIT = 10;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorUserId,
                       AdminAuditAction action,
                       String targetType,
                       Object targetId,
                       String summary,
                       String reason,
                       Map<String, Object> metadata) {
        adminAuditLogRepository.save(AdminAuditLog.create(
                actorUserId,
                action,
                targetType,
                targetId == null ? null : String.valueOf(targetId),
                summary,
                reason,
                toJson(metadata)
        ));
    }

    /** Ticket completion audit commits or rolls back with its receipt, balance and history. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTicketAdjustment(Long actorUserId, Long userId, String operationId,
                                       int amount, int before, int after, String reason) {
        adminAuditLogRepository.save(AdminAuditLog.create(actorUserId, AdminAuditAction.TICKET_ADJUSTED,
                "USER", String.valueOf(userId), "사용자 #" + userId + " 티켓을 " + amount + "만큼 조정했습니다.",
                reason, toJson(Map.of("operationId", operationId, "amount", amount,
                        "beforeTickets", before, "afterTickets", after))));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordApiCall(Long actorUserId,
                              String method,
                              String path,
                              int httpStatus,
                              long durationMs,
                              String traceId) {
        String normalizedMethod = abbreviate(trimToNull(method), 12);
        String normalizedPath = abbreviate(trimToNull(path), 200);
        if (normalizedMethod == null || normalizedPath == null) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", normalizedMethod);
        metadata.put("path", normalizedPath);
        metadata.put("httpStatus", httpStatus);
        metadata.put("durationMs", durationMs);
        metadata.put("traceId", traceId);

        adminAuditLogRepository.save(AdminAuditLog.createApiCall(
                actorUserId,
                normalizedMethod,
                normalizedPath,
                httpStatus,
                durationMs,
                abbreviate(normalizedMethod + " " + normalizedPath + " API를 호출했습니다.", 300),
                toJson(metadata)
        ));
    }

    /** Only change flags are recorded: internal notes never enter the general audit feed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReportUpdate(Long actorId, Long reportId, String beforeStatus, String afterStatus,
                                   boolean memoChanged, boolean replyChanged, Long previousVersion) {
        adminAuditLogRepository.save(AdminAuditLog.create(actorId, AdminAuditAction.REPORT_STATUS_UPDATED,
                "REPORT", String.valueOf(reportId), "신고 #" + reportId + " 처리 정보를 저장했습니다.", null,
                toJson(Map.of("beforeStatus", beforeStatus, "status", afterStatus,
                        "internalMemoChanged", memoChanged, "reporterReplyChanged", replyChanged,
                        "previousVersion", previousVersion))));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReportUserAction(Long actorId, Long reportId, Long userId,
                                      AdminRequests.UserActionRequest request, String reason) {
        AdminAuditLog entry = AdminAuditLog.create(actorId, AdminAuditAction.USER_ACTION_APPLIED,
                "USER", String.valueOf(userId), "신고 #" + reportId + " 대상 사용자 #" + userId
                        + "에게 " + request.action().name() + " 조치를 적용했습니다.", reason,
                toJson(Map.of("userId", userId, "action", request.action().name(),
                        "until", request.until() == null ? "" : request.until().toString())));
        entry.linkToReport(reportId);
        adminAuditLogRepository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LocalDateTime recordChatMessageInspection(Long actorUserId,
                                                     Long roomId,
                                                     AdminRequests.ChatMessageInspectionRequest request,
                                                     List<Long> messageIds,
                                                     int deletedCount,
                                                     LocalDateTime from,
                                                     LocalDateTime to,
                                                     String traceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("from", from);
        metadata.put("to", to);
        metadata.put("page", request.page());
        metadata.put("size", request.size());
        metadata.put("returnedMessageIds", messageIds);
        metadata.put("returnedCount", messageIds.size());
        metadata.put("deletedCount", deletedCount);
        metadata.put("result", "CONTENT_ACCESS_GRANTED");
        metadata.put("traceId", traceId);
        AdminAuditLog log = AdminAuditLog.create(
                actorUserId,
                AdminAuditAction.CHAT_MESSAGES_INSPECTED,
                "CHAT_ROOM",
                String.valueOf(roomId),
                "관리자 대화 본문 제공 승인 (클라이언트 수신 여부는 확인하지 않음)",
                request.reason().name(),
                toJson(metadata)
        );
        adminAuditLogRepository.saveAndFlush(log);
        // API message/range timestamps use UTC, independently of the legacy audit table clock.
        return LocalDateTime.now(java.time.Clock.systemUTC());
    }

    @Transactional
    public long deleteExpiredChatInspectionLogs(LocalDateTime cutoff) {
        return adminAuditLogRepository.deleteByActionAndCreatedAtBefore(
                AdminAuditAction.CHAT_MESSAGES_INSPECTED,
                cutoff
        );
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResponse<AdminDtos.AuditLogItem> search(Integer page,
                                                                 Integer size,
                                                                 Long actorUserId,
                                                                 AdminAuditAction action,
                                                                 String targetType) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<AdminDtos.AuditLogItem> mapped = adminAuditLogRepository
                .search(actorUserId, action, trimToNull(targetType), pageable)
                .map(log -> new AdminDtos.AuditLogItem(
                        log.getId(),
                        log.getActorUserId(),
                        log.getAction(),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getSummary(),
                        log.getReason(),
                        log.getMetadataJson(),
                        log.getCreatedAt()
                ));
        return AdminDtos.PageResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public AdminDtos.AuditLogStatistics statistics(Integer requestedDays) {
        int days = safeStatsDays(requestedDays);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        return new AdminDtos.AuditLogStatistics(
                days,
                since,
                adminAuditLogRepository.countByCreatedAtGreaterThanEqual(since),
                adminAuditLogRepository.countByActionAndCreatedAtGreaterThanEqual(AdminAuditAction.ADMIN_API_CALLED, since),
                adminAuditLogRepository.countDistinctActorsSince(since),
                adminAuditLogRepository.countApiCallsSince(since, PageRequest.of(0, STATS_LIMIT)).stream()
                        .map(row -> new AdminDtos.ApiCallCount(
                                row.getMethod(),
                                row.getPath(),
                                row.getCount(),
                                row.getAverageDurationMs()
                        ))
                        .toList(),
                adminAuditLogRepository.countActionsSince(
                                since,
                                AdminAuditAction.ADMIN_API_CALLED,
                                PageRequest.of(0, STATS_LIMIT)
                        ).stream()
                        .map(row -> new AdminDtos.AuditActionCount(row.getAction(), row.getCount()))
                        .toList(),
                LocalDateTime.now()
        );
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "관리자 감사 로그 metadata 생성에 실패했습니다.");
        }
    }

    private int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int safeStatsDays(Integer days) {
        if (days == null || days < 1) {
            return DEFAULT_STATS_DAYS;
        }
        return Math.min(days, MAX_STATS_DAYS);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
