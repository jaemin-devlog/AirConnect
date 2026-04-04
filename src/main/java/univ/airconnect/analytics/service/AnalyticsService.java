package univ.airconnect.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;
import univ.airconnect.analytics.dto.request.AnalyticsTrackItemRequest;
import univ.airconnect.analytics.dto.request.AnalyticsTrackRequest;
import univ.airconnect.analytics.dto.response.AnalyticsTrackResponse;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalyticsTrackResponse trackClientEvents(Long userId, AnalyticsTrackRequest request) {
        List<AnalyticsEvent> events = request.getEvents().stream()
                .map(item -> toClientEvent(userId, item))
                .toList();

        analyticsEventRepository.saveAll(events);

        return AnalyticsTrackResponse.builder()
                .savedCount(events.size())
                .trackedAt(LocalDateTime.now())
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackServerEvent(AnalyticsEventType eventType, Long userId, Map<String, Object> payload) {
        if (eventType == null || userId == null) {
            return;
        }

        try {
            analyticsEventRepository.save(
                    AnalyticsEvent.server(
                            userId,
                            eventType,
                            LocalDateTime.now(),
                            toPayloadJson(payload)
                    )
            );
        } catch (Exception e) {
            log.warn("통계 서버 이벤트 저장을 건너뜁니다. type={}, userId={}, reason={}",
                    eventType, userId, e.getMessage());
        }
    }

    private AnalyticsEvent toClientEvent(Long userId, AnalyticsTrackItemRequest item) {
        return AnalyticsEvent.client(
                userId,
                item.getType(),
                item.getSessionId(),
                item.getDeviceId(),
                item.getScreenName(),
                item.getOccurredAt(),
                toPayloadJson(item.getPayload())
        );
    }

    private String toPayloadJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("통계 payload 직렬화에 실패했습니다. reason={}", e.getMessage());
            return "{}";
        }
    }
}
