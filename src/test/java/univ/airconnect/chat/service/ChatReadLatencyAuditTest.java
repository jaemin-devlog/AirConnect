package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.repository.*;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Local H2/service diagnostics. Redis/broker are mocks; timings are NOT production latency. */
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ChatService.class, ChatDeliveryService.class, ChatDeliveryDispatcher.class,
        StompSessionRegistry.class, JacksonAutoConfiguration.class})
class ChatReadLatencyAuditTest {
    @Autowired ChatService chat;
    @Autowired ChatDeliveryDispatcher dispatcher;
    ChatDeliveryWorker worker;
    @Autowired ChatDeliveryEventRepository events;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatRoomMemberRepository members;
    @Autowired ChatRoomRepository rooms;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager txManager;
    @Autowired ObjectMapper mapper;
    @MockitoBean RedisTemplate<String, Object> redis;
    @MockitoBean RedisMessageListenerContainer listener;
    @MockitoBean RedisSubscriber subscriber;
    @MockitoBean SimpMessageSendingOperations broker;
    @MockitoBean NotificationService notifications;
    @MockitoBean UserBlockPolicyService blocks;
    @MockitoBean ChatMessageThrottleService chatMessageThrottleService;
    @MockitoBean univ.airconnect.auth.security.AccessTokenRevocationService accessTokenRevocationService;
    Long author, reader, room;
    org.mockito.MockedStatic<java.time.Clock> databasePrecisionClock;

    @BeforeEach void setupWorker() {
        databasePrecisionClock = DatabasePrecisionChatClock.open();
        worker = new ChatDeliveryWorker(events, dispatcher);
    }

    @AfterEach void cleanup() {
        try {
            new TransactionTemplate(txManager).executeWithoutResult(s -> {
                events.deleteAllInBatch();
                messages.deleteAllInBatch();
                members.deleteAllInBatch();
                rooms.deleteAllInBatch();
                users.deleteAllInBatch();
            });
        } finally {
            databasePrecisionClock.close();
        }
    }

    List<ChatMessage> seed(int participants, int count) {
        return new TransactionTemplate(txManager).execute(s -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < participants; i++) {
                User u = User.create(SocialProvider.KAKAO, UUID.randomUUID().toString(), "audit@test.invalid");
                u.completeSignUp("audit", "audit", 20240001, "test");
                ids.add(users.saveAndFlush(u).getId());
            }
            author = ids.get(0); reader = ids.get(1);
            room = participants == 2
                    ? chat.createNewPersonalRoomForConnection(null, author, reader, "audit").getId()
                    : chat.createGroupRoomWithMembers("audit", ids).getId();
            List<ChatMessage> incoming = new ArrayList<>();
            for (int i = 0; i < count; i++)
                incoming.add(ChatMessage.create(room, author, "audit", "message-" + i, MessageType.TEXT));
            return messages.saveAllAndFlush(incoming);
        });
    }

    @ParameterizedTest
    @CsvSource({"2,1", "2,20", "2,100", "2,500", "4,20", "6,100"})
    void measureReadCommitAndPublish(int participants, int count) {
        var incoming = seed(participants, count);
        AtomicInteger receipts = new AtomicInteger();
        AtomicLong firstPublish = new AtomicLong();
        doAnswer(call -> {
            ChatMessageResponse r = mapper.readValue((String) call.getArgument(1), ChatMessageResponse.class);
            assertThat(r.getEventType()).isEqualTo("READ_RECEIPT");
            assertThat(r.getUnreadCount()).isEqualTo(participants - 2);
            firstPublish.compareAndSet(0, System.nanoTime());
            receipts.incrementAndGet();
            return null;
        }).when(redis).convertAndSend(eq(room.toString()), any());

        long started = System.nanoTime();
        chat.markMessagesReadThrough(room, reader, incoming.get(count - 1).getId());
        long returned = System.nanoTime();
        assertThat(receipts.get()).isEqualTo(count);
        assertThat(events.count()).isZero();
        assertThat(members.findByChatRoomIdAndUserId(room, reader).orElseThrow().getLastReadMessageId())
                .isEqualTo(incoming.get(count - 1).getId());
        assertThat(messages.countUnreadByUserId(reader)).isEmpty();
        System.out.printf(Locale.ROOT,
                "READ_AUDIT participants=%d messages=%d firstPublishMs=%.2f serviceReturnMs=%.2f receipts=%d%n",
                participants, count, (firstPublish.get() - started) / 1e6, (returned - started) / 1e6, receipts.get());
    }

    @Test void redisRecoveryDrainsReadReceiptsInOneWorkerTick() {
        var incoming = seed(2, 20);
        doThrow(new IllegalStateException("simulated Redis outage"))
                .when(redis).convertAndSend(eq(room.toString()), any());
        chat.markMessagesReadThrough(room, reader, incoming.get(19).getId());
        assertThat(messages.countUnreadByUserId(reader)).isEmpty();
        assertThat(events.count()).isEqualTo(20);

        reset(redis);
        worker.retryPending();
        verify(redis, times(20)).convertAndSend(eq(room.toString()), any());
        assertThat(events.count()).isZero();
        System.out.println("READ_AUDIT recovery messages=20 workerTicks=1 DB_unread=0_before_delivery");
    }

    @Test void restHistoryFetchAloneDoesNotMarkRead() {
        seed(2, 3);
        assertThat(chat.findMessagesByRoomId(room, reader, null, 20)).hasSize(3);
        assertThat(members.findByChatRoomIdAndUserId(room, reader).orElseThrow().getLastReadMessageId()).isNull();
        assertThat(messages.countUnreadByUserId(reader)).singleElement()
                .satisfies(row -> assertThat(((Number) row[1]).intValue()).isEqualTo(3));
        verifyNoInteractions(redis);
    }

    @Test void partialBatchFailureRetainsAllReceiptsAndDoesNotOvertakeFailedBatch() {
        var incoming = seed(2, 120);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(call -> {
            if (attempts.incrementAndGet() == 6) throw new IllegalStateException("Redis interrupted mid-batch");
            return null;
        }).when(redis).convertAndSend(eq(room.toString()), any());
        chat.markMessagesReadThrough(room, reader, incoming.get(119).getId());
        assertThat(attempts.get()).isEqualTo(6);
        assertThat(events.count()).isEqualTo(120);
        assertThat(messages.countUnreadByUserId(reader)).isEmpty();

        reset(redis);
        worker.retryPending();
        var payloads = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(redis, times(120)).convertAndSend(eq(room.toString()), payloads.capture());
        var receivedIds = payloads.getAllValues().stream().map(payload -> {
            try { return mapper.readValue((String) payload, ChatMessageResponse.class).getId(); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }).toList();
        assertThat(receivedIds).containsExactlyElementsOf(incoming.stream().map(ChatMessage::getId).toList());
        assertThat(events.count()).isZero();
    }

    @Test void readBatchCannotOvertakeOlderLaneEvent() throws Exception {
        var incoming = seed(2, 3);
        var older = ChatMessageResponse.readReceipt(room, incoming.get(0).getId(),
                java.time.LocalDateTime.now(java.time.Clock.systemUTC()), 1);
        events.saveAndFlush(univ.airconnect.chat.domain.entity.ChatDeliveryEvent.create(
                univ.airconnect.chat.domain.entity.ChatDeliveryEvent.Kind.MESSAGE, room,
                incoming.get(0).getId(), null, mapper.writeValueAsString(older)));
        chat.markMessagesReadThrough(room, reader, incoming.get(2).getId());
        verifyNoInteractions(redis);
        assertThat(events.count()).isEqualTo(4);
        worker.retryPending();
        var payloads = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(redis, times(4)).convertAndSend(eq(room.toString()), payloads.capture());
        assertThat(mapper.readValue((String) payloads.getAllValues().get(0), ChatMessageResponse.class).getUnreadCount()).isEqualTo(1);
        for (int i = 1; i < 4; i++)
            assertThat(mapper.readValue((String) payloads.getAllValues().get(i), ChatMessageResponse.class).getUnreadCount()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test void rolledBackReadDoesNotPublishOrPersistReceipts() {
        var incoming = seed(2, 3);
        assertThatThrownBy(() -> new TransactionTemplate(txManager).executeWithoutResult(status -> {
            chat.markMessagesReadThrough(room, reader, incoming.get(2).getId());
            throw new IllegalStateException("rollback read");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(events.count()).isZero();
        assertThat(members.findByChatRoomIdAndUserId(room, reader).orElseThrow().getLastReadMessageId()).isNull();
        verifyNoInteractions(redis);
    }
}
