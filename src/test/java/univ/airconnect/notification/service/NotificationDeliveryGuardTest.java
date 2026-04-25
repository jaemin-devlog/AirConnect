package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.service.fcm.FcmDataPayloadMapper;
import univ.airconnect.notification.service.fcm.FcmDataPayloadValidator;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicyResolver;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryGuardTest {

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    private NotificationDeliveryGuard guard;

    @BeforeEach
    void setUp() {
        guard = new NotificationDeliveryGuard(
                pushDeviceRepository,
                notificationPreferenceService,
                new AndroidPushPolicyResolver(),
                new FcmDataPayloadMapper(new ObjectMapper(), new FcmDataPayloadValidator()),
                new ObjectMapper()
        );
    }

    @Test
    void evaluate_defersAndroidOutboxDuringQuietHoursWhenStillWithinTtl() {
        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        NotificationOutbox outbox = NotificationOutbox.create(
                100L,
                1L,
                17L,
                PushProvider.FCM,
                "token-1",
                "Match request",
                "You have a new request.",
                "{\"notificationType\":\"MATCH_REQUEST_RECEIVED\",\"requestId\":\"900\"}",
                nowUtc.minusMinutes(1)
        );
        ReflectionTestUtils.setField(outbox, "id", 1000L);

        PushDevice pushDevice = androidDevice(1L, "token-1");
        LocalDateTime nextAllowedAt = nowUtc.plusMinutes(20);

        when(pushDeviceRepository.findById(17L)).thenReturn(Optional.of(pushDevice));
        when(notificationPreferenceService.getDeliveryPolicy(1L, univ.airconnect.notification.domain.NotificationType.MATCH_REQUEST_RECEIVED))
                .thenReturn(NotificationPreferenceService.DeliveryPolicy.defer(true, nextAllowedAt, "QUIET_HOURS"));

        GuardResult result = guard.evaluate(outbox);

        assertThat(result.decision()).isEqualTo(DeliveryDecision.DEFER);
        assertThat(result.nextAttemptAt()).isEqualTo(nextAllowedAt);
    }

    @Test
    void evaluate_skipsExpiredAndroidOutbox() {
        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        NotificationOutbox outbox = NotificationOutbox.create(
                101L,
                2L,
                17L,
                PushProvider.FCM,
                "token-2",
                "Reminder",
                "Appointment soon",
                "{\"notificationType\":\"APPOINTMENT_REMINDER_10M\",\"appointmentId\":\"55\"}",
                nowUtc.minusMinutes(1)
        );
        ReflectionTestUtils.setField(outbox, "id", 1001L);
        ReflectionTestUtils.setField(outbox, "createdAt", nowUtc.minusMinutes(11));

        when(pushDeviceRepository.findById(17L)).thenReturn(Optional.of(androidDevice(2L, "token-2")));
        when(notificationPreferenceService.getDeliveryPolicy(2L, univ.airconnect.notification.domain.NotificationType.APPOINTMENT_REMINDER_10M))
                .thenReturn(NotificationPreferenceService.DeliveryPolicy.sendNow(true));

        GuardResult result = guard.evaluate(outbox);

        assertThat(result.decision()).isEqualTo(DeliveryDecision.SKIP);
        assertThat(result.reason()).isEqualTo("OUTBOX_EXPIRED");
    }

    private PushDevice androidDevice(Long userId, String token) {
        PushDevice device = PushDevice.register(
                userId,
                "device-1",
                PushPlatform.ANDROID,
                PushProvider.FCM,
                token,
                null,
                true,
                "1.0.0",
                "14",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.now(Clock.systemUTC())
        );
        ReflectionTestUtils.setField(device, "id", 17L);
        return device;
    }
}
