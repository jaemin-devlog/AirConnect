package univ.airconnect.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.matching.domain.entity.MatchingNotificationEvent;
import univ.airconnect.matching.repository.MatchingNotificationEventRepository;
import univ.airconnect.notification.domain.*;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.*;
import univ.airconnect.notification.service.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import({MatchingNotificationDispatcher.class, NotificationService.class, MatchingNotificationPersistenceTest.Config.class})
class MatchingNotificationPersistenceTest {
    @TestConfiguration
    static class Config { @Bean ObjectMapper objectMapper() { return new ObjectMapper(); } }
    @Autowired MatchingNotificationDispatcher dispatcher;
    @Autowired MatchingNotificationEventRepository events;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationOutboxRepository outboxes;
    @Autowired PushDeviceRepository devices;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean NotificationPreferenceService preferences;
    @MockitoBean PushDeviceService pushDevices;

    @Test
    void realNotificationAndPushOutboxCommitOnceAndFailedAttemptRetainsEvent() throws Exception {
        var device = devices.saveAndFlush(PushDevice.builder().userId(7L).deviceId("test-device")
                .platform(PushPlatform.ANDROID).provider(PushProvider.FCM).pushToken("test-token")
                .notificationPermissionGranted(true).build());
        var command = new NotificationService.CreateCommand(7L, NotificationType.MATCH_REQUEST_RECEIVED,
                "새 요청", "연결 요청", "airconnect://matching/requests", 8L, null,
                "{\"connectionId\":1}", "test-event");
        var event = events.saveAndFlush(MatchingNotificationEvent.create(7L, 8L, mapper.writeValueAsString(command)));
        when(preferences.getDeliveryPolicy(anyLong(), any()))
                .thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
        // Failure after notification insertion must roll back both notification and delivery state.
        when(pushDevices.findPushableDevices(7L)).thenThrow(new IllegalStateException("temporary device lookup failure"));
        assertThatThrownBy(() -> dispatcher.dispatch(event.getId())).isInstanceOf(RuntimeException.class);
        assertThat(events.existsById(event.getId())).isTrue();
        assertThat(notifications.count()).isZero();
        assertThat(outboxes.count()).isZero();
        doReturn(List.of(device)).when(pushDevices).findPushableDevices(7L);
        dispatcher.dispatch(event.getId());
        dispatcher.dispatch(event.getId());
        assertThat(events.existsById(event.getId())).isFalse();
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(outboxes.count()).isEqualTo(1);
    }

    @Test
    void rolledBackBusinessTransactionCannotLeaveNotificationIntent() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.saveAndFlush(MatchingNotificationEvent.create(7L, 8L, "{}"));
            status.setRollbackOnly();
        });
        assertThat(events.count()).isZero();
        assertThat(notifications.count()).isZero();
    }
}
