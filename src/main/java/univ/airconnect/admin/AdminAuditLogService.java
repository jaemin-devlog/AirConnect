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

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
