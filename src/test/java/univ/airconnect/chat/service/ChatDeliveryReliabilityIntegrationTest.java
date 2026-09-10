package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.entity.ChatDeliveryEvent;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.repository.*;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.domain.*;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.*;
import univ.airconnect.notification.service.*;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import({ChatService.class, ChatDeliveryService.class, ChatDeliveryDispatcher.class, StompSessionRegistry.class,
        NotificationService.class, NotificationOutboxDispatchService.class, JacksonAutoConfiguration.class})
class ChatDeliveryReliabilityIntegrationTest {
    @Autowired ChatService chat;
    @Autowired ChatDeliveryDispatcher deliveries;
    @Autowired ChatDeliveryEventRepository events;
    @Autowired ChatMessageRepository messages;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationOutboxRepository outboxes;
    @Autowired PushDeviceRepository devices;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager txManager;
    @Autowired NotificationOutboxDispatchService pushes;
    @MockitoBean NotificationPreferenceService preferences;
    @MockitoBean PushDeviceService pushDevices;
    @MockitoBean PushNotificationSender sender;
    @MockitoBean RedisTemplate<String, Object> redis;
    @MockitoBean RedisMessageListenerContainer redisListener;
    @MockitoBean RedisSubscriber redisSubscriber;
    @MockitoBean SimpMessageSendingOperations broker;
    @MockitoBean UserBlockPolicyService blockPolicy;
    Long author, recipient, room;
    PushDevice device;

    @BeforeEach void setup() {
        author = users.saveAndFlush(user("author")).getId();
        recipient = users.saveAndFlush(user("recipient")).getId();
        room = chat.createGroupRoomWithMembers("test", List.of(author, recipient)).getId();
        device = devices.saveAndFlush(PushDevice.builder().userId(recipient).deviceId("device")
                .platform(PushPlatform.ANDROID).provider(PushProvider.FCM).pushToken("old-token")
                .notificationPermissionGranted(true).build());
        when(preferences.getDeliveryPolicy(anyLong(), any())).thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
        when(pushDevices.findPushableDevices(recipient)).thenReturn(List.of(device));
    }

    @Test void notificationFailureCannotRollbackMessageAndRetryCreatesOneNotification() {
        when(pushDevices.findPushableDevices(recipient)).thenThrow(new IllegalStateException("notification database unavailable"));
        var response = chat.sendMessage(author, room, request("durable", "id-1"));
        assertThat(messages.existsById(response.getId())).isTrue();
        assertThat(notifications.count()).isZero();
        assertThat(events.findAll()).singleElement().satisfies(e -> assertThat(e.getKind()).isEqualTo(ChatDeliveryEvent.Kind.NOTIFICATION));
        doReturn(List.of(device)).when(pushDevices).findPushableDevices(recipient);
        Long id = events.findAll().get(0).getId();
        deliveries.deliver(id);
        deliveries.deliver(id);
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(outboxes.count()).isEqualTo(1);
        assertThat(events.count()).isZero();
    }

    @Test void realtimeFailureSurvivesAndRetryCannotExposeDeletedContent() {
        doThrow(new IllegalStateException("Redis down")).when(redis).convertAndSend(anyString(), any());
        var response = chat.sendMessage(author, room, request("secret-before-delete", "id-2"));
        chat.deleteMessage(author, room, response.getId());
        assertThat(messages.existsById(response.getId())).isTrue();
        assertThat(events.count()).isGreaterThanOrEqualTo(2);
        reset(redis);
        for (var event : events.findAll()) deliveries.deliver(event.getId());
        var payloads = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(redis, atLeastOnce()).convertAndSend(eq(room.toString()), payloads.capture());
        assertThat(payloads.getAllValues()).allSatisfy(p -> assertThat(p.toString()).doesNotContain("secret-before-delete"));
        assertThat(events.count()).isZero();
    }

    @Test void businessRollbackRemovesMessageAndEveryDeliveryIntent() {
        assertThatThrownBy(() -> new TransactionTemplate(txManager).executeWithoutResult(status -> {
            chat.sendMessage(author, room, request("rollback", "id-3"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(messages.count()).isZero();
        assertThat(events.count()).isZero();
        assertThat(notifications.count()).isZero();
        verifyNoInteractions(redis);
    }

    @Test void slowProviderDoesNotHoldChatLocksAndLateInvalidTokenCannotRevokeRefreshedDevice() throws Exception {
        var message = chat.sendMessage(author, room, request("first", "id-4"));
        var outbox = outboxes.findAll().get(0);
        new TransactionTemplate(txManager).executeWithoutResult(status -> outboxes.findById(outbox.getId()).orElseThrow().claim());
        var future = new CompletableFuture<PushNotificationSender.PushSendResult>();
        var started = new CountDownLatch(1);
        when(sender.sendAsync(any(), any())).thenAnswer(invocation -> { started.countDown(); return future; });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var sending = executor.submit(() -> pushes.dispatch(outbox.getId()));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            // Both operations need the same room/member locks that used to stay held during FCM.
            executor.submit(() -> {
                chat.markMessagesReadThrough(room, recipient, message.getId());
                chat.sendMessage(recipient, room, request("reply", "id-5"));
                new TransactionTemplate(txManager).executeWithoutResult(status -> devices.findByIdForUpdate(device.getId())
                        .orElseThrow().refreshToken("new-token", null, true, "1", "1", "ko", "Asia/Seoul", java.time.LocalDateTime.now()));
            }).get(5, TimeUnit.SECONDS);
            future.complete(PushNotificationSender.PushSendResult.invalidToken("UNREGISTERED", "old token"));
            sending.get(5, TimeUnit.SECONDS);
            assertThat(devices.findById(device.getId()).orElseThrow().getPushToken()).isEqualTo("new-token");
            assertThat(devices.findById(device.getId()).orElseThrow().getActive()).isTrue();
        } finally {
            future.complete(PushNotificationSender.PushSendResult.success("cleanup"));
            executor.shutdownNow();
        }
    }

    @Test void recoveredAttemptCannotBeOverwrittenByOldProviderCompletion() throws Exception {
        chat.sendMessage(author, room, request("fenced", "id-6"));
        Long id = outboxes.findAll().get(0).getId();
        new TransactionTemplate(txManager).executeWithoutResult(status -> outboxes.findById(id).orElseThrow().claim());
        var response = new CompletableFuture<PushNotificationSender.PushSendResult>();
        var started = new CountDownLatch(1);
        when(sender.sendAsync(any(), any())).thenAnswer(invocation -> { started.countDown(); return response; });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var sending = executor.submit(() -> pushes.dispatch(id));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            String replacement = new TransactionTemplate(txManager).execute(status -> {
                var outbox = outboxes.findByIdForUpdate(id).orElseThrow();
                outbox.markRetry("RECOVERED", "simulated lease recovery", java.time.LocalDateTime.now());
                outbox.claim();
                return outbox.beginDispatch();
            });
            response.complete(PushNotificationSender.PushSendResult.success("late-old-provider-id"));
            sending.get(5, TimeUnit.SECONDS);
            var current = outboxes.findById(id).orElseThrow();
            assertThat(current.getStatus()).isEqualTo(NotificationDeliveryStatus.PROCESSING);
            assertThat(current.getDispatchToken()).isEqualTo(replacement);
            assertThat(current.getProviderMessageId()).isNull();
        } finally {
            response.complete(PushNotificationSender.PushSendResult.success("cleanup"));
            executor.shutdownNow();
        }
    }

    private User user(String name) {
        var user = User.create(SocialProvider.KAKAO, UUID.randomUUID().toString(), name + "@test.dev");
        user.completeSignUp(name, name, 20240001, "test");
        return user;
    }
    private SendMessageRequest request(String content, String id) {
        var request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "content", content);
        ReflectionTestUtils.setField(request, "clientMessageId", id);
        return request;
    }
}
