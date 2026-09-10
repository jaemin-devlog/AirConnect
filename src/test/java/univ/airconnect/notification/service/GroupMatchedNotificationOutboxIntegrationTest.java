package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DataJpaTest
@ActiveProfiles("test")
@Import({
        NotificationService.class,
        NotificationPreferenceService.class,
        PushDeviceService.class,
        NotificationOutboxDispatchService.class,
        JacksonAutoConfiguration.class
})
class GroupMatchedNotificationOutboxIntegrationTest {

    private static final Long FINAL_GROUP_ROOM_ID = 201L;
    private static final Long FINAL_CHAT_ROOM_ID = 301L;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;
    @Autowired
    private PushDeviceService pushDeviceService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationOutboxDispatchService dispatchService;

    @MockitoBean
    private PushNotificationSender pushNotificationSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void groupMatchedCreatesNotificationAndOutboxPerRecipientWithCanonicalPayload() throws Exception {
        User first = saveUserWithDevice("group-first", "device-first", "token-first");
        User second = saveUserWithDevice("group-second", "device-second", "token-second");

        enqueueGroupMatched(first.getId());
        enqueueGroupMatched(second.getId());

        List<Notification> notifications = notificationRepository.findAll().stream()
                .sorted(Comparator.comparing(Notification::getUserId))
                .toList();
        List<NotificationOutbox> outboxes = notificationOutboxRepository.findAll();

        assertThat(notifications).hasSize(2);
        assertThat(outboxes).hasSize(2);
        assertThat(notifications).allSatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.GROUP_MATCHED);
            assertThat(notification.getDedupeKey()).isEqualTo("group-matched:201");
            assertThat(notification.getDeeplink()).isEqualTo("airconnect://group-chat/final/201");
        });
        for (NotificationOutbox outbox : outboxes) {
            JsonNode payload = objectMapper.readTree(outbox.getDataJson());
            assertThat(payload.get("notificationType").asText()).isEqualTo("GROUP_MATCHED");
            assertThat(payload.get("finalGroupRoomId").asLong()).isEqualTo(FINAL_GROUP_ROOM_ID);
            assertThat(payload.get("finalChatRoomId").asLong()).isEqualTo(FINAL_CHAT_ROOM_ID);
            assertThat(payload.get("deeplink").asText()).isEqualTo("airconnect://group-chat/final/201");
        }
    }

    @Test
    void duplicateGroupMatchedEventDoesNotCreateDuplicateNotificationOrOutbox() {
        User user = saveUserWithDevice("group-dedupe", "device-dedupe", "token-dedupe");

        enqueueGroupMatched(user.getId());
        enqueueGroupMatched(user.getId());

        assertThat(notificationRepository.findAll()).hasSize(1);
        assertThat(notificationOutboxRepository.findAll()).hasSize(1);
    }

    @Test
    void activeGroupMatchedOutboxDispatchesThroughPhaseOneService() {
        User user = saveUserWithDevice("group-active", "device-active", "token-active");
        enqueueGroupMatched(user.getId());
        NotificationOutbox outbox = notificationOutboxRepository.findAll().get(0);
        outbox.claim();
        notificationOutboxRepository.saveAndFlush(outbox);
        when(pushNotificationSender.sendAsync(any(NotificationOutbox.class), any(PushPlatform.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(PushNotificationSender.PushSendResult.success("fcm-group-message")));

        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        dispatchService.dispatch(outbox.getId());

        NotificationOutbox reloaded = notificationOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(reloaded.getProviderMessageId()).isEqualTo("fcm-group-message");
        verify(pushNotificationSender).sendAsync(any(NotificationOutbox.class), any(PushPlatform.class));
    }

    @Test
    void groupMatchedOutboxStillUsesPhaseOneRecipientValidation() {
        User user = saveUserWithDevice("group-stale", "device-stale", "token-stale");
        enqueueGroupMatched(user.getId());
        NotificationOutbox outbox = notificationOutboxRepository.findAll().get(0);
        outbox.claim();
        notificationOutboxRepository.saveAndFlush(outbox);
        pushDeviceService.deactivateIfPresent(user.getId(), "device-stale");

        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        dispatchService.dispatch(outbox.getId());

        NotificationOutbox reloaded = notificationOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationDeliveryStatus.SKIPPED);
        assertThat(reloaded.getLastErrorCode())
                .isEqualTo(NotificationOutboxDispatchService.PUSH_DEVICE_INACTIVE);
        verifyNoInteractions(pushNotificationSender);
    }

    private User saveUserWithDevice(String socialId, String deviceId, String token) {
        User user = userRepository.saveAndFlush(
                User.create(SocialProvider.APPLE, socialId, socialId + "@airconnect.test")
        );
        pushDeviceService.registerOrUpdate(new PushDeviceService.UpsertCommand(
                user.getId(),
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
        return user;
    }

    private Notification enqueueGroupMatched(Long userId) {
        return notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                userId,
                NotificationType.GROUP_MATCHED,
                "그룹 매칭 성사",
                "상대 팀과 매칭됐어요. 최종 그룹 채팅방으로 이동해보세요.",
                "airconnect://group-chat/final/" + FINAL_GROUP_ROOM_ID,
                null,
                null,
                "{\"team1RoomId\":101,\"team2RoomId\":102,\"finalGroupRoomId\":201,"
                        + "\"finalChatRoomId\":301,\"memberCount\":2}",
                "group-matched:" + FINAL_GROUP_ROOM_ID
        ));
    }
}
