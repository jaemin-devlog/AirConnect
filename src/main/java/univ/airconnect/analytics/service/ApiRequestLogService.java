package univ.airconnect.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.domain.entity.ApiRequestLog;
import univ.airconnect.analytics.repository.ApiRequestLogRepository;

@Service
@RequiredArgsConstructor
public class ApiRequestLogService {

    private final ApiRequestLogRepository apiRequestLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId,
                       String method,
                       String path,
                       Integer httpStatus,
                       long durationMs,
                       String traceId) {
        String normalizedMethod = abbreviate(trimToNull(method), 12);
        String normalizedPath = abbreviate(trimToNull(path), 200);
        if (normalizedMethod == null || normalizedPath == null) {
            return;
        }

        apiRequestLogRepository.save(ApiRequestLog.create(
                userId,
                normalizedMethod,
                normalizedPath,
                httpStatus,
                Math.max(0L, durationMs),
                traceId
        ));
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
