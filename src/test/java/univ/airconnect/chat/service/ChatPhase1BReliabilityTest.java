package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.dto.request.ChatMessageRequest;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(ChatSendStatusSecurityTest.JpaConfig.class)
class ChatPhase1BReliabilityTest {

    @Autowired ChatService chatService;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomMemberRepository members;
    @Autowired ChatMessageRepository messages;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean RedisTemplate<String, Object> redisTemplate;
    @MockitoBean RedisMessageListenerContainer redisListener;
    @MockitoBean RedisSubscriber redisSubscriber;
    @MockitoBean SimpMessageSendingOperations messagingTemplate;
    @MockitoBean NotificationService notifications;
    @MockitoBean UserBlockPolicyService blockPolicy;

    private TransactionTemplate transaction;
    private User sender;
    private User recipient;
    private User thirdUser;
    private Long firstRoomId;
    private Long secondRoomId;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            sender = users.save(user("sender"));
            recipient = users.save(user("recipient"));
            thirdUser = users.save(user("third"));
            firstRoomId = chatService.createGroupRoomWithMembers(
                    "phase-1b-first", List.of(sender.getId(), recipient.getId())).getId();
            secondRoomId = chatService.createGroupRoomWithMembers(
                    "phase-1b-second", List.of(sender.getId(), thirdUser.getId())).getId();
        });
        clearExternalEffects();
    }

    @AfterEach
    void cleanUp() {
        transaction.executeWithoutResult(status -> {
            messages.deleteAllInBatch();
            members.deleteAllInBatch();
            rooms.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @Test
    void messageAndRoomListPublishOnlyAfterCommit() {
        AtomicReference<ChatMessageResponse> response = new AtomicReference<>();

        transaction.executeWithoutResult(status -> {
            response.set(send(sender.getId(), firstRoomId, "committed", "commit-1"));
            assertThat(messages.countByRoomId(firstRoomId)).isEqualTo(1);
            verify(redisTemplate, never()).convertAndSend(anyString(), any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        });

        assertThat(response.get().getId()).isNotNull();
        verify(redisTemplate).convertAndSend(eq(firstRoomId.toString()), any());
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/list/" + sender.getId()), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/list/" + recipient.getId()), any(Object.class));
    }

    @Test
    void rollbackDoesNotPublishMessageOrRoomList() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            send(sender.getId(), firstRoomId, "rolled back", "rollback-1");
            verify(redisTemplate, never()).convertAndSend(anyString(), any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(messages.countByRoomId(firstRoomId)).isZero();
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void notificationPersistenceRollbackDoesNotLeakRealtimeMessage() {
        doAnswer(invocation -> {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return null;
        }).when(notifications).createAndEnqueue(any(NotificationService.CreateCommand.class));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                send(sender.getId(), firstRoomId, "notification rollback", "rollback-2")))
                .isInstanceOf(UnexpectedRollbackException.class);

        assertThat(messages.countByRoomId(firstRoomId)).isZero();
        verify(notifications).createAndEnqueue(any(NotificationService.CreateCommand.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void redisFailureAfterCommitKeepsMessageRecoverableThroughRestQuery() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisTemplate).convertAndSend(anyString(), any());

        AtomicReference<ChatMessageResponse> sent = new AtomicReference<>();
        assertThatCode(() -> sent.set(send(sender.getId(), firstRoomId, "durable", "redis-1")))
                .doesNotThrowAnyException();

        assertThat(messages.countByRoomId(firstRoomId)).isEqualTo(1);
        List<ChatMessageResponse> recovered = chatService.findMessagesByRoomId(
                firstRoomId, recipient.getId(), null, 20);
        assertThat(recovered).extracting(ChatMessageResponse::getId).contains(sent.get().getId());
    }

    @Test
    void readReceiptRollbackDoesNotPublish() {
        ChatMessageResponse sent = send(sender.getId(), firstRoomId, "unread", "read-1");
        clearExternalEffects();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            chatService.syncReadStateOnRoomViewed(firstRoomId, recipient.getId());
            verify(redisTemplate, never()).convertAndSend(anyString(), any());
            throw new IllegalStateException("force read rollback");
        })).isInstanceOf(IllegalStateException.class);

        verify(redisTemplate, never()).convertAndSend(anyString(), any());
        assertThat(messages.findById(sent.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void retryWithSameClientMessageIdReturnsExistingMessage() {
        ChatMessageResponse first = send(sender.getId(), firstRoomId, "same", "retry-1");
        ChatMessageResponse retry = send(sender.getId(), firstRoomId, "same", "retry-1");

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(retry.getClientMessageId()).isEqualTo("retry-1");
        assertThat(messages.countByRoomId(firstRoomId)).isEqualTo(1);
        verify(notifications).createAndEnqueue(any(NotificationService.CreateCommand.class));
    }

    @Test
    void concurrentRetryConvergesToOneMessage() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ChatMessageResponse> first = executor.submit(() -> concurrentSend(ready, start));
            Future<ChatMessageResponse> second = executor.submit(() -> concurrentSend(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ChatMessageResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            ChatMessageResponse secondResponse = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResponse.getId()).isEqualTo(secondResponse.getId());
            assertThat(messages.countByRoomId(firstRoomId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameClientMessageIdWithDifferentPayloadReturnsConflict() {
        ChatMessageResponse first = send(sender.getId(), firstRoomId, "original", "conflict-1");

        assertThatThrownBy(() -> send(sender.getId(), firstRoomId, "changed", "conflict-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_MESSAGE_IDEMPOTENCY_CONFLICT);

        assertThat(messages.countByRoomId(firstRoomId)).isEqualTo(1);
        assertThat(messages.findById(first.getId()).orElseThrow().getDisplayContent()).isEqualTo("original");
    }

    @Test
    void sameClientMessageIdIsScopedBySenderAndRoom() {
        ChatMessageResponse first = send(sender.getId(), firstRoomId, "sender first", "scoped-1");
        ChatMessageResponse otherSender = send(recipient.getId(), firstRoomId, "sender second", "scoped-1");
        ChatMessageResponse otherRoom = send(sender.getId(), secondRoomId, "room second", "scoped-1");

        assertThat(List.of(first.getId(), otherSender.getId(), otherRoom.getId())).doesNotHaveDuplicates();
        assertThat(messages.count()).isEqualTo(3);
    }

    @Test
    void missingClientMessageIdKeepsLegacyAtLeastOnceBehavior() {
        ChatMessageResponse first = send(sender.getId(), firstRoomId, "legacy", null);
        ChatMessageResponse second = send(sender.getId(), firstRoomId, "legacy", null);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(messages.countByRoomId(firstRoomId)).isEqualTo(2);
    }

    @Test
    void restThenStompRetryUsesSameIdempotencyPolicy() {
        ChatMessageResponse restResponse = send(sender.getId(), firstRoomId, "cross transport", "cross-1");
        ChatMessageRequest stompRequest = new ChatMessageRequest();
        ReflectionTestUtils.setField(stompRequest, "roomId", firstRoomId);
        ReflectionTestUtils.setField(stompRequest, "message", "cross transport");
        ReflectionTestUtils.setField(stompRequest, "messageType", MessageType.TEXT);
        ReflectionTestUtils.setField(stompRequest, "clientMessageId", "cross-1");

        chatService.sendMessage(sender.getId(), stompRequest);

        assertThat(messages.countByRoomId(firstRoomId)).isEqualTo(1);
        Long storedMessageId = transaction.execute(status ->
                messages.findByRoomIdAndSenderIdAndClientMessageId(
                        firstRoomId, sender.getId(), "cross-1").orElseThrow().getId());
        assertThat(storedMessageId).isEqualTo(restResponse.getId());
    }

    private ChatMessageResponse concurrentSend(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return send(sender.getId(), firstRoomId, "concurrent", "concurrent-1");
    }

    private ChatMessageResponse send(Long userId, Long roomId, String content, String clientMessageId) {
        SendMessageRequest request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "content", content);
        ReflectionTestUtils.setField(request, "messageType", MessageType.TEXT);
        ReflectionTestUtils.setField(request, "clientMessageId", clientMessageId);
        return chatService.sendMessage(userId, roomId, request);
    }

    private void clearExternalEffects() {
        clearInvocations(redisTemplate, redisListener, messagingTemplate, notifications);
    }

    private User user(String nickname) {
        return User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId(UUID.randomUUID().toString())
                .nickname(nickname)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(10)
                .build();
    }
}
