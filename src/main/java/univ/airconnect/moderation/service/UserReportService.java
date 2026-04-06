package univ.airconnect.moderation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.moderation.dto.request.CreateUserReportRequest;
import univ.airconnect.moderation.dto.response.UserReportResponse;
import univ.airconnect.moderation.exception.ModerationErrorCode;
import univ.airconnect.moderation.exception.ModerationException;
import univ.airconnect.moderation.infrastructure.ModerationProperties;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReportService {

    private final UserRepository userRepository;
    private final UserReportRepository userReportRepository;
    private final ModerationProperties moderationProperties;

    @Transactional
    public UserReportResponse createReport(Long reporterUserId, CreateUserReportRequest request) {
        if (request == null) {
            throw new ModerationException(ModerationErrorCode.REPORT_TARGET_NOT_FOUND);
        }
        validateUsers(reporterUserId, request.getReportedUserId());

        if (reporterUserId.equals(request.getReportedUserId())) {
            throw new ModerationException(ModerationErrorCode.REPORT_SELF_NOT_ALLOWED);
        }

        ReportSourceType sourceType = request.getSourceType() == null
                ? ReportSourceType.OTHER
                : request.getSourceType();
        String sourceId = normalizeNullableText(request.getSourceId());
        String detail = normalizeNullableText(request.getDetail());

        long duplicateWindowMinutes = Math.max(1L, moderationProperties.getReport().getDuplicateWindowMinutes());
        LocalDateTime duplicateAfter = LocalDateTime.now().minusMinutes(duplicateWindowMinutes);
        boolean duplicated = userReportRepository.existsRecentDuplicate(
                reporterUserId,
                request.getReportedUserId(),
                request.getReportReason(),
                sourceType,
                sourceId,
                ReportStatus.OPEN,
                duplicateAfter
        );
        if (duplicated) {
            throw new ModerationException(ModerationErrorCode.REPORT_DUPLICATE);
        }

        UserReport saved = userReportRepository.save(
                UserReport.createReceived(
                        reporterUserId,
                        request.getReportedUserId(),
                        request.getReportReason(),
                        detail,
                        sourceType,
                        sourceId
                )
        );
        return UserReportResponse.from(saved);
    }

    public List<UserReportResponse> getMyReports(Long reporterUserId) {
        ensureReporterExists(reporterUserId);
        return userReportRepository.findTop50ByReporterUserIdOrderByCreatedAtDesc(reporterUserId).stream()
                .map(UserReportResponse::from)
                .toList();
    }

    private void validateUsers(Long reporterUserId, Long reportedUserId) {
        ensureReporterExists(reporterUserId);
        ensureReportedUserExists(reportedUserId);
    }

    private void ensureReporterExists(Long reporterUserId) {
        User reporter = userRepository.findById(reporterUserId)
                .orElseThrow(() -> new ModerationException(ModerationErrorCode.REPORTER_NOT_FOUND));
        if (reporter.getStatus() == UserStatus.DELETED) {
            throw new ModerationException(ModerationErrorCode.REPORTER_NOT_FOUND);
        }
    }

    private void ensureReportedUserExists(Long reportedUserId) {
        User reportedUser = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new ModerationException(ModerationErrorCode.REPORT_TARGET_NOT_FOUND));
        if (reportedUser.getStatus() == UserStatus.DELETED) {
            throw new ModerationException(ModerationErrorCode.REPORT_TARGET_NOT_FOUND);
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
