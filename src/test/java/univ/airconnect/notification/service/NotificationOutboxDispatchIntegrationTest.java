package univ.airconnect.notification.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DataJpaTest
@ActiveProfiles("test")
@Import({NotificationOutboxDispatchService.class, PushDeviceService.class})
class NotificationOutboxDispatchIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PushDeviceRepository pushDeviceRepository;
    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;
    @Autowired
    private NotificationOutboxDispatchService dispatchService;
    @Autowired
    private PushDeviceService pushDeviceService;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PushNotificationSender pushNotificationSender;

    @Test
    void logoutMakesExistingProcessingOutboxIneligible() {
        User user = saveUser("logout-user");
        PushDevice device = saveDevice(user.getId(), "device-1", "token-1");
        NotificationOutbox outbox = saveProcessingOutbox(user.getId(), device.getId(), "token-1");

        pushDeviceService.deactivateIfPresent(user.getId(), "device-1");
        flushAndClear();
        dispatchService.dispatch(outbox.getId());

        assertSkipped(outbox.getId(), NotificationOutboxDispatchService.PUSH_DEVICE_INACTIVE);
        verifyNoInteractions(pushNotificationSender);
    }

    @Test
    void accountSwitchReleasesOldOwnershipBeforeOldOutboxCanSend() {
        User previousUser = saveUser("previous-user");
        User nextUser = saveUser("next-user");
        PushDevice previousDevice = saveDevice(previousUser.getId(), "shared-device", "shared-token");
        NotificationOutbox oldOutbox = saveProcessingOutbox(
                previousUser.getId(), previousDevice.getId(), "shared-token");

        pushDeviceService.registerOrUpdate(command(nextUser.getId(), "shared-device", "shared-token"));
        flushAndClear();
        dispatchService.dispatch(oldOutbox.getId());

        PushDevice releasedPreviousDevice = pushDeviceRepository.findById(previousDevice.getId()).orElseThrow();
        assertThat(releasedPreviousDevice.getActive()).isFalse();
        assertThat(releasedPreviousDevice.getPushToken()).startsWith("released:");
        assertSkipped(oldOutbox.getId(), NotificationOutboxDispatchService.PUSH_DEVICE_INACTIVE);
        verifyNoInteractions(pushNotificationSender);
    }

    @Test
    void deletedRecipientMakesExistingProcessingOutboxIneligible() {
        User user = saveUser("deleted-user");
        PushDevice device = saveDevice(user.getId(), "device-3", "token-3");
        NotificationOutbox outbox = saveProcessingOutbox(user.getId(), device.getId(), "token-3");

        user.markDeleted();
        flushAndClear();
        dispatchService.dispatch(outbox.getId());

        assertSkipped(outbox.getId(), NotificationOutboxDispatchService.RECIPIENT_NOT_ACTIVE);
        verifyNoInteractions(pushNotificationSender);
    }

    @Test
    void suspendedRecipientCanReceiveOnlyTheCommittedSuspensionAnnouncement() {
        User user = saveUser("suspended-user");
        PushDevice device = saveDevice(user.getId(), "device-suspended", "token-suspended");
        NotificationOutbox outbox = saveProcessingOutbox(
                user.getId(),
                device.getId(),
                "token-suspended",
                "{\"notificationType\":\"SYSTEM_ANNOUNCEMENT\","
                        + "\"kind\":\"ADMIN_USER_ACTION\",\"action\":\"SUSPEND\"}"
        );
        user.suspend(LocalDateTime.now().plusDays(1), "운영 정책 위반");
        when(pushNotificationSender.sendAsync(any(NotificationOutbox.class), eq(PushPlatform.ANDROID)))
                .thenReturn(CompletableFuture.completedFuture(
                        PushNotificationSender.PushSendResult.success("fcm-suspension-integration")));

        flushAndClear();
        dispatchService.dispatch(outbox.getId());

        NotificationOutbox reloaded = notificationOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .withFailMessage("Expected suspension Push to be sent, but code=%s message=%s payload=%s",
                        reloaded.getLastErrorCode(), reloaded.getLastErrorMessage(), reloaded.getDataJson())
                .isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(reloaded.getProviderMessageId()).isEqualTo("fcm-suspension-integration");
    }

    @Test
    void tokenRefreshMakesOldTokenOutboxIneligible() {
        User user = saveUser("refresh-user");
        PushDevice device = saveDevice(user.getId(), "device-4", "token-old");
        NotificationOutbox outbox = saveProcessingOutbox(user.getId(), device.getId(), "token-old");

        pushDeviceService.registerOrUpdate(command(user.getId(), "device-4", "token-new"));
        flushAndClear();
        dispatchService.dispatch(outbox.getId());

        assertSkipped(outbox.getId(), NotificationOutboxDispatchService.PUSH_TOKEN_CHANGED);
        verifyNoInteractions(pushNotificationSender);
    }

    private User saveUser(String socialId) {
        return userRepository.saveAndFlush(
                User.create(SocialProvider.APPLE, socialId, socialId + "@airconnect.test")
        );
    }

    private PushDevice saveDevice(Long userId, String deviceId, String token) {
        return pushDeviceRepository.saveAndFlush(PushDevice.register(
                userId,
                deviceId,
                PushPlatform.ANDROID,
                PushProvider.FCM,
                token,
                null,
                true,
                "1.0.0",
                "15",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.now()
        ));
    }

    private NotificationOutbox saveProcessingOutbox(Long userId, Long pushDeviceId, String token) {
        return saveProcessingOutbox(
                userId,
                pushDeviceId,
                token,
                "{\"notificationType\":\"MATCH_REQUEST_RECEIVED\"}"
        );
    }

    private NotificationOutbox saveProcessingOutbox(Long userId,
                                                     Long pushDeviceId,
                                                     String token,
                                                     String dataJson) {
        NotificationOutbox outbox = NotificationOutbox.create(
                10_000L + pushDeviceId,
                userId,
                pushDeviceId,
                PushProvider.FCM,
                token,
                "테스트 알림",
                "테스트 본문",
                dataJson,
                LocalDateTime.now()
        );
        outbox.claim();
        return notificationOutboxRepository.saveAndFlush(outbox);
    }

    private PushDeviceService.UpsertCommand command(Long userId, String deviceId, String token) {
        return new PushDeviceService.UpsertCommand(
                userId,
                deviceId,
                PushPlatform.ANDROID,
                PushProvider.FCM,
                token,
                null,
                true,
                "1.0.1",
                "15",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.now()
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
    }

    private void assertSkipped(Long outboxId, String expectedReason) {
        NotificationOutbox reloaded = notificationOutboxRepository.findById(outboxId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationDeliveryStatus.SKIPPED);
        assertThat(reloaded.getLastErrorCode()).isEqualTo(expectedReason);
    }
}
