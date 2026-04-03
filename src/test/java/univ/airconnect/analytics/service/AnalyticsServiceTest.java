package univ.airconnect.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;
import univ.airconnect.analytics.dto.request.AnalyticsTrackItemRequest;
import univ.airconnect.analytics.dto.request.AnalyticsTrackRequest;
import univ.airconnect.analytics.dto.response.AnalyticsTrackResponse;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(analyticsEventRepository, new ObjectMapper());
    }

    @Test
    void trackClientEvents_savesAllEvents() {
        AnalyticsTrackItemRequest item = new AnalyticsTrackItemRequest(
                AnalyticsEventType.SCREEN_VIEWED,
                LocalDateTime.of(2026, 4, 3, 12, 0),
                "session-1",
                "device-1",
                "main",
                Map.of("tab", "home")
        );
        AnalyticsTrackRequest request = new AnalyticsTrackRequest(List.of(item));

        when(analyticsEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        AnalyticsTrackResponse response = analyticsService.trackClientEvents(1L, request);

        ArgumentCaptor<List<AnalyticsEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(analyticsEventRepository).saveAll(captor.capture());

        assertThat(response.getSavedCount()).isEqualTo(1);
        assertThat(response.getTrackedAt()).isNotNull();
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().get(0).getType()).isEqualTo(AnalyticsEventType.SCREEN_VIEWED);
        assertThat(captor.getValue().get(0).getScreenName()).isEqualTo("main");
        assertThat(captor.getValue().get(0).getSessionId()).isEqualTo("session-1");
        assertThat(captor.getValue().get(0).getDeviceId()).isEqualTo("device-1");
        assertThat(captor.getValue().get(0).getPayloadJson()).contains("\"tab\":\"home\"");
    }
}
