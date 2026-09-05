package univ.airconnect.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {
    private final UserReportRepository reports;
    private final UserRepository users;
    private final ChatMessageRepository messages;
    private final ChatRoomRepository rooms;
    private final ChatRoomMemberRepository members;
    private final MatchingConnectionRepository connections;
    private final AdminAuditLogRepository auditLogs;
    private final AdminAuditLogService audits;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper;
    private final AdminService adminService;

    public AdminDtos.ReportDetail get(Long adminId, Long reportId) {
        requireAdmin(adminId);
        return detail(requiredReport(reportId));
    }

    @Transactional
    public AdminDtos.ReportDetail update(Long adminId, Long reportId, AdminRequests.ReportStatusUpdateRequest request) {
        User actor = requireAdmin(adminId);
        validate(request);
        if (reportId == null || reportId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        UserReport report = reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다."));
        if (!Objects.equals(report.getVersion(), request.expectedVersion())) {
            throw new BusinessException(ErrorCode.REPORT_CONFLICT);
        }
        ReportStatus previousStatus = report.getStatus();
        boolean memoChanged = !Objects.equals(report.getInternalMemo(), request.internalMemo());
        boolean replyChanged = !Objects.equals(report.getReporterReply(), request.reporterReply());
        Long previousVersion = report.getVersion();
        report.updateHandling(request.status(), request.internalMemo(), request.reporterReply(), adminId);
        if (previousStatus != request.status()) {
            notifyReporter(actor, report, previousVersion);
        }
        audits.recordReportUpdate(adminId, reportId, previousStatus.name(), request.status().name(),
                memoChanged, replyChanged, previousVersion);
        // Flush the optimistic version before mapping; audit/notification failure rolls it all back.
        reports.flush();
        return detail(report);
    }

    public AdminDtos.ChatMessageInspection inspectEvidence(Long adminId, Long reportId,
                                                           AdminRequests.ReportEvidenceInspectionRequest request,
                                                           String traceId) {
        requireAdmin(adminId);
        if (request == null || request.roomId() == null || request.roomId() <= 0
                || request.reason() != AdminRequests.ChatInspectionReason.REPORT_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "신고 검토 사유와 증거 대화방을 확인해 주세요.");
        }
        // A prior detail response is not an authorization token: always re-check the source relationship.
        AdminDtos.ReportEvidence evidence = evidence(requiredReport(reportId));
        if (!"AVAILABLE".equals(evidence.status()) || !Objects.equals(evidence.roomId(), request.roomId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 신고와 연결된 대화 증거를 확인할 수 없습니다.");
        }
        return adminService.inspectChatMessages(adminId, request.roomId(),
                new AdminRequests.ChatMessageInspectionRequest(request.reason(), request.from(), request.to(),
                        request.page(), request.size()), traceId);
    }

    public AdminDtos.ChatHistory readEvidenceHistory(Long adminId, Long reportId,
                                                     AdminRequests.ReportHistoryRequest request, String traceId) {
        requireAdmin(adminId);
        if (request == null || request.roomId() == null || request.roomId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var source = evidence(requiredReport(reportId));
        if (!"AVAILABLE".equals(source.status()) || !Objects.equals(source.roomId(), request.roomId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 신고와 연결된 대화 증거를 확인할 수 없습니다.");
        }
        return adminService.readChatHistory(adminId, request.roomId(),
                new AdminRequests.ChatHistoryRequest(request.beforeId(), request.size()), traceId);
    }

    private AdminDtos.ReportDetail detail(UserReport report) {
        String reporterName = users.findById(report.getReporterUserId()).map(this::nickname).orElse(null);
        String reportedName = users.findById(report.getReportedUserId()).map(this::nickname).orElse(null);
        var record = new AdminDtos.ReportRecord(report.getId(), report.getReporterUserId(), reporterName,
                report.getReportedUserId(), reportedName, report.getReason(), report.getDetail(), report.getStatus(),
                report.getCreatedAt(), report.getUpdatedAt());
        List<AdminDtos.ReportAction> actions = auditLogs
                .findByActionAndReportIdOrderByCreatedAtDescIdDesc(AdminAuditAction.USER_ACTION_APPLIED, report.getId())
                .stream().map(this::action).toList();
        return new AdminDtos.ReportDetail(record, report.getVersion(), report.getInternalMemo(), report.getReporterReply(),
                report.getHandledByUserId(), evidence(report), actions);
    }

    private AdminDtos.ReportEvidence evidence(UserReport report) {
        ReportSourceType type = report.getSourceType();
        Long source = positiveSourceId(report.getSourceId());
        Long reporter = report.getReporterUserId();
        Long subject = report.getReportedUserId();
        if (source == null || type == null || reporter == null || subject == null || reporter.equals(subject)) {
            return unavailable(type);
        }
        return switch (type) {
            case PROFILE -> source.equals(subject) && users.existsById(subject)
                    ? available(type, subject, null, null, null) : unavailable(type);
            case CHAT_MESSAGE -> messages.findReportSourceById(source)
                    .filter(message -> Objects.equals(message.getSenderId(), subject))
                    .filter(message -> message.getId() != null && message.getRoomId() != null && message.getRoomId() > 0)
                    .filter(message -> rooms.existsById(message.getRoomId()))
                    .filter(message -> members.existsByChatRoomIdAndUserId(message.getRoomId(), reporter))
                    .map(message -> available(type, subject, message.getRoomId(), null, message.getId()))
                    .orElseGet(() -> unavailable(type));
            case CHAT_ROOM -> validRoom(source, reporter, subject)
                    ? available(type, subject, source, null, null) : unavailable(type);
            case MATCHING_REQUEST -> connections.findById(source)
                    .filter(connection -> connection.isParticipant(reporter) && connection.isParticipant(subject))
                    .map(connection -> available(type, subject,
                            validRoom(connection.getChatRoomId(), reporter, subject) ? connection.getChatRoomId() : null,
                            connection.getId(), null))
                    .orElseGet(() -> unavailable(type));
            case OTHER -> unavailable(type);
        };
    }

    private boolean validRoom(Long roomId, Long reporter, Long subject) {
        // Hidden membership is retained when a victim blocks someone; hiding is not evidence deletion.
        return roomId != null && rooms.existsById(roomId)
                && members.existsByChatRoomIdAndUserId(roomId, reporter)
                && members.existsByChatRoomIdAndUserId(roomId, subject);
    }

    private AdminDtos.ReportEvidence available(ReportSourceType type, Long userId, Long roomId,
                                                Long connectionId, Long messageId) {
        return new AdminDtos.ReportEvidence("AVAILABLE", type, userId, roomId, connectionId, messageId);
    }

    private AdminDtos.ReportEvidence unavailable(ReportSourceType type) {
        return new AdminDtos.ReportEvidence("UNAVAILABLE", type, null, null, null, null);
    }

    private Long positiveSourceId(String supplied) {
        if (supplied == null || !supplied.matches("[0-9]{1,19}")) return null;
        try {
            long value = Long.parseLong(supplied);
            return value > 0 ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private AdminDtos.ReportAction action(AdminAuditLog entry) {
        try {
            JsonNode metadata = objectMapper.readTree(entry.getMetadataJson());
            // JSON columns can be returned as a JSON string scalar by some JDBC dialects.
            if (metadata != null && metadata.isTextual()) metadata = objectMapper.readTree(metadata.textValue());
            if (metadata == null || !metadata.isObject() || !metadata.path("userId").canConvertToLong()
                    || metadata.path("userId").longValue() <= 0 || !metadata.path("action").isTextual()) {
                throw new IllegalArgumentException("Invalid report action metadata");
            }
            String until = metadata.path("until").asText("");
            String action = AdminRequests.UserActionType.valueOf(metadata.path("action").asText()).name();
            return new AdminDtos.ReportAction(entry.getId(), entry.getActorUserId(), metadata.path("userId").longValue(),
                    action, entry.getReason(), until.isBlank() ? null : LocalDateTime.parse(until),
                    entry.getCreatedAt());
        } catch (Exception invalid) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "신고 조치 기록을 확인할 수 없습니다.");
        }
    }

    private void notifyReporter(User actor, UserReport report, Long previousVersion) {
        String reply = report.getReporterReply();
        String body = switch (report.getStatus()) {
            case IN_REVIEW -> "신고가 검토중입니다. 빠른 시일 내에 처리됩니다.";
            case RESOLVED -> "신고가 처리되었습니다. " + reply;
            case REJECTED -> "신고가 기각되었습니다. 사유: " + reply;
            default -> null;
        };
        if (body == null) return;
        String senderName = actor.getName() != null && !actor.getName().isBlank() ? actor.getName().trim()
                : actor.getNickname() != null && !actor.getNickname().isBlank() ? actor.getNickname().trim() : "운영팀";
        try {
            String payload = objectMapper.writeValueAsString(Map.of("kind", "ADMIN_REPORT_STATUS_UPDATE",
                    "reportId", report.getId(), "status", report.getStatus().name(),
                    "reason", reply == null ? "" : reply, "reportedUserId", report.getReportedUserId(),
                    "senderName", senderName, "senderUserId", actor.getId()));
            notifications.createAndEnqueue(new NotificationService.CreateCommand(report.getReporterUserId(),
                    NotificationType.SYSTEM_ANNOUNCEMENT, senderName, body, null, actor.getId(), null, payload,
                    "admin-report:" + report.getId() + ":version:" + previousVersion));
        } catch (JsonProcessingException failure) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "신고 알림 생성에 실패했습니다.");
        }
    }

    private User requiredAdmin(Long adminId) {
        return users.findById(adminId).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private User requireAdmin(Long adminId) {
        if (adminId == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        User user = requiredAdmin(adminId);
        if (user.getRole() != UserRole.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private UserReport requiredReport(Long reportId) {
        if (reportId == null || reportId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return reports.findById(reportId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다."));
    }

    private String nickname(User user) {
        return user.getNickname() == null ? user.getName() : user.getNickname();
    }

    private void validate(AdminRequests.ReportStatusUpdateRequest request) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0 || request.status() == null
                || (request.internalMemo() != null && request.internalMemo().length() > 1000)
                || (request.reporterReply() != null && request.reporterReply().length() > 300)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "신고 버전과 처리 내용을 확인해 주세요.");
        }
        if ((request.status() == ReportStatus.RESOLVED || request.status() == ReportStatus.REJECTED)
                && request.reporterReply() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "종결 상태에는 신고자에게 전달할 답변이 필요합니다.");
        }
    }
}
