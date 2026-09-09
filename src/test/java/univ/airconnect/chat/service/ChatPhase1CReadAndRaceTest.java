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
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(ChatSendStatusSecurityTest.JpaConfig.class)
class ChatPhase1CReadAndRaceTest {

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
                    "phase-1c-first", List.of(sender.getId(), recipient.getId())).getId();
            secondRoomId = chatService.createGroupRoomWithMembers(
                    "phase-1c-second", List.of(sender.getId(), thirdUser.getId())).getId();
        });
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
    void getLatestHundredOfOneThousandUnreadMessagesDoesNotAdvanceReadCursor() {
        transaction.executeWithoutResult(status -> {
            List<ChatMessage> unread = new ArrayList<>(1_000);
            for (int i = 0; i < 1_000; i++) {
                unread.add(ChatMessage.create(
                        firstRoomId, sender.getId(), sender.getNickname(), "message-" + i, MessageType.TEXT));
            }
            messages.saveAll(unread);
        });

        List<ChatMessageResponse> page = chatService.findMessagesByRoomId(
                firstRoomId, recipient.getId(), null, 100);

        assertThat(page).hasSize(100);
        assertThat(member(firstRoomId, recipient.getId()).getLastReadMessageId()).isNull();
        assertThat(messages.countUnreadByUserId(recipient.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row[1]).isEqualTo(1_000L));
    }

    @Test
    void explicitReadAdvancesOnlyThroughRequestedMessage() {
        List<ChatMessage> saved = saveIncomingMessages(firstRoomId, 3);

        chatService.markMessagesReadThrough(firstRoomId, recipient.getId(), saved.get(1).getId());

        assertThat(member(firstRoomId, recipient.getId()).getLastReadMessageId()).isEqualTo(saved.get(1).getId());
        assertThat(messages.findById(saved.get(0).getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(messages.findById(saved.get(1).getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(messages.findById(saved.get(2).getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void delayedLowerReadRequestCannotMoveCursorBackward() {
        List<ChatMessage> saved = saveIncomingMessages(firstRoomId, 3);
        chatService.markMessagesReadThrough(firstRoomId, recipient.getId(), saved.get(2).getId());

        chatService.markMessagesReadThrough(firstRoomId, recipient.getId(), saved.get(0).getId());

        assertThat(member(firstRoomId, recipient.getId()).getLastReadMessageId()).isEqualTo(saved.get(2).getId());
    }

    @Test
    void readRequestForMessageInAnotherRoomIsRejected() {
        ChatMessage foreignMessage = transaction.execute(status -> messages.save(ChatMessage.create(
                secondRoomId, thirdUser.getId(), thirdUser.getNickname(), "foreign", MessageType.TEXT)));

        assertThatThrownBy(() -> chatService.markMessagesReadThrough(
                firstRoomId, recipient.getId(), foreignMessage.getId()))
                .isInstanceOf(BusinessException.class);

        assertThat(member(firstRoomId, recipient.getId()).getLastReadMessageId()).isNull();
    }

    @Test
    void sendingDoesNotMarkEarlierIncomingMessagesAsRead() {
        List<ChatMessage> incoming = saveIncomingMessages(firstRoomId, 3);

        ChatMessageResponse sent = send(recipient.getId(), firstRoomId, "quick reply", "phase-1c-quick-reply");

        assertThat(sent.getId()).isNotNull();
        assertThat(member(firstRoomId, recipient.getId()).getLastReadMessageId()).isNull();
        assertThat(incoming)
                .allSatisfy(message -> assertThat(messages.findById(message.getId()).orElseThrow().getReadAt()).isNull());
    }

    @Test
    void deletedGroupMemberIsExcludedFromNewMessageUnreadCount() {
        Long groupRoomId = transaction.execute(status -> chatService.createGroupRoomWithMembers(
                "phase-1c-deleted-member",
                List.of(sender.getId(), recipient.getId(), thirdUser.getId())).getId());
        transaction.executeWithoutResult(status -> users.findByIdForUpdate(thirdUser.getId()).orElseThrow().markDeleted());

        ChatMessageResponse response = send(sender.getId(), groupRoomId, "active recipients only", "phase-1c-active");

        assertThat(response.getUnreadCount()).isEqualTo(1);
    }

    @Test
    void leaveCommitAfterInitialSendAuthorizationPreventsTextMessageInsert() throws Exception {
        assertRevocationRacePreventsSend(() -> chatService.leaveRoom(firstRoomId, sender.getId()), BusinessException.class);
    }

    @Test
    void expelCommitAfterInitialSendAuthorizationPreventsTextMessageInsert() throws Exception {
        assertRevocationRacePreventsSend(
                () -> chatService.removeMember(firstRoomId, sender.getId()),
                BusinessException.class
        );
    }

    @Test
    void deleteAccountCommitAfterInitialSendAuthorizationPreventsTextMessageInsert() throws Exception {
        assertRevocationRacePreventsSend(() -> transaction.executeWithoutResult(status ->
                users.findByIdForUpdate(sender.getId()).orElseThrow().markDeleted()), AuthException.class);
    }

    @Test
    void normalSendStillPersistsTextMessage() {
        ChatMessageResponse response = send(sender.getId(), firstRoomId, "normal", "phase-1c-normal");

        assertThat(response.getId()).isNotNull();
        assertThat(messages.findById(response.getId())).isPresent();
    }

    private void assertRevocationRacePreventsSend(Runnable revoke, Class<? extends Throwable> expected)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch initiallyAuthorized = new CountDownLatch(1);
        CountDownLatch revocationCommitted = new CountDownLatch(1);
        try {
            Future<ChatMessageResponse> send = executor.submit(() -> transaction.execute(status -> {
                assertThat(members.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(firstRoomId, sender.getId())).isTrue();
                initiallyAuthorized.countDown();
                await(revocationCommitted);
                return send(sender.getId(), firstRoomId, "must not persist", UUID.randomUUID().toString());
            }));

            assertThat(initiallyAuthorized.await(5, TimeUnit.SECONDS)).isTrue();
            revoke.run();
            revocationCommitted.countDown();

            assertThatThrownBy(() -> send.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(expected);
            assertThat(messages.findByRoomIdOrderByCreatedAtAsc(firstRoomId))
                    .noneMatch(message -> message.getType() == MessageType.TEXT);
        } finally {
            revocationCommitted.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for committed revocation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for committed revocation", exception);
        }
    }

    private List<ChatMessage> saveIncomingMessages(Long roomId, int count) {
        return transaction.execute(status -> {
            List<ChatMessage> incoming = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                incoming.add(ChatMessage.create(
                        roomId, sender.getId(), sender.getNickname(), "incoming-" + i, MessageType.TEXT));
            }
            return messages.saveAll(incoming);
        });
    }

    private ChatRoomMember member(Long roomId, Long userId) {
        return members.findByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId).orElseThrow();
    }

    private ChatMessageResponse send(Long userId, Long roomId, String content, String clientMessageId) {
        SendMessageRequest request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "content", content);
        ReflectionTestUtils.setField(request, "messageType", MessageType.TEXT);
        ReflectionTestUtils.setField(request, "clientMessageId", clientMessageId);
        return chatService.sendMessage(userId, roomId, request);
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
