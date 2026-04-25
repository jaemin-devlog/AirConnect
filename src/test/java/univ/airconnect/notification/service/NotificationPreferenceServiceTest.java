package univ.airconnect.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.NotificationPreference;
import univ.airconnect.notification.repository.NotificationPreferenceRepository;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(notificationPreferenceRepository);
    }

    @Test
    void getDeliveryPolicy_returnsDeferForDelayableTypeDuringQuietHours() {
        NotificationPreference preference = activeQuietHoursPreference(1L);
        when(notificationPreferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));

        NotificationPreferenceService.DeliveryPolicy policy =
                service.getDeliveryPolicy(1L, NotificationType.CHAT_MESSAGE_RECEIVED);

        assertThat(policy.pushDecision()).isEqualTo(PushDecision.DEFER);
        assertThat(policy.nextAllowedAt()).isNotNull();
        assertThat(policy.reason()).isEqualTo("QUIET_HOURS");
    }

    @Test
    void getDeliveryPolicy_returnsSkipForNonDelayableReminderDuringQuietHours() {
        NotificationPreference preference = activeQuietHoursPreference(2L);
        when(notificationPreferenceRepository.findByUserId(2L)).thenReturn(Optional.of(preference));

        NotificationPreferenceService.DeliveryPolicy policy =
                service.getDeliveryPolicy(2L, NotificationType.APPOINTMENT_REMINDER_10M);

        assertThat(policy.pushDecision()).isEqualTo(PushDecision.SKIP);
        assertThat(policy.reason()).isEqualTo("QUIET_HOURS");
    }

    private NotificationPreference activeQuietHoursPreference(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(Clock.systemUTC()).withZoneSameInstant(ZoneId.of("Asia/Seoul"));
        return NotificationPreference.builder()
                .userId(userId)
                .quietHoursEnabled(true)
                .quietHoursStart(now.minusMinutes(30).toLocalTime())
                .quietHoursEnd(now.plusMinutes(30).toLocalTime())
                .timezone("Asia/Seoul")
                .build();
    }
}
