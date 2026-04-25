package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.tx.AfterCommitExecutor;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceReadTrackingTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private RedisMessageListenerContainer redisMessageListener;
    @Mock private RedisSubscriber redisSubscriber;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private SimpMessageSendingOperations messagingTemplate;
    @Mock private NotificationService notificationService;
    @Mock private UserBlockPolicyService userBlockPolicyService;

    private ObjectMapper objectMapper;
    private ChatService chatService;
    private Set<Long> viewingUserIds;
    private List<ChatMessageResponse> publishedEvents;
    private AtomicLong messageIdSequence;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        viewingUserIds = new HashSet<>();
        publishedEvents = new ArrayList<>();
        messageIdSequence = new AtomicLong(1000L);

        ChatService rawService = new ChatService(
                chatRoomRepository,
                chatMessageRepository,
                chatRoomMemberRepository,
                userRepository,
                redisMessageListener,
                redisSubscriber,
                redisTemplate,
                messagingTemplate,
                objectMapper,
                notificationService,
                userBlockPolicyService,
                new AfterCommitExecutor()
        );
        chatService = spy(rawService);

        doAnswer(invocation -> viewingUserIds.contains(invocation.getArgument(0)))
                .when(chatService)
                .isUserViewingRoom(anyLong(), anyLong());

        doAnswer(invocation -> {
            String payload = invocation.getArgument(1, String.class);
            publishedEvents.add(objectMapper.readValue(payload, ChatMessageResponse.class));
            return null;
        }).when(redisTemplate).convertAndSend(any(String.class), any(String.class));

        when(userBlockPolicyService.findAnyBlockedCounterpart(anyLong(), anyList())).thenReturn(Optional.empty());
    }

    @Test
    void personalRoomSubscribeImmediatelyClearsExistingUnread() {
        Long roomId = 10L;
        Long senderId = 1L;
        Long readerId = 2L;

        ChatRoom room = personalRoom(roomId, senderId, readerId);
        ChatRoomMember senderMember = member(room, senderId, utcNow().minusMinutes(10));
        ChatRoomMember readerMember = member(room, readerId, utcNow().minusMinutes(10));
        senderMember.updateLastReadMessageId(500L);

        ChatMessage message = message(500L, roomId, senderId, "hello", MessageType.TEXT, utcNow().minusMinutes(1));
        List<ChatMessage> roomMessages = new ArrayList<>(List.of(message));

        stubRoom(room, roomMessages, Map.of(senderId, senderMember, readerId, readerMember));

        chatService.syncReadStateOnRoomViewed(roomId, readerId);

        assertEquals(500L, readerMember.getLastReadMessageId());
        assertNotNull(message.getReadAt());
        assertEquals(1, publishedEvents.size());
        assertEquals("READ_RECEIPT", publishedEvents.get(0).getEventType());
        assertEquals(0, publishedEvents.get(0).getUnreadCount());
        assertEquals(500L, publishedEvents.get(0).getMessageId());
    }

    @Test
    void personalRoomMessageIsImmediatelyReadWhenCounterpartIsViewing() {
        Long roomId = 11L;
        Long senderId = 1L;
        Long counterpartId = 2L;

        ChatRoom room = personalRoom(roomId, senderId, counterpartId);
        ChatRoomMember senderMember = member(room, senderId, utcNow().minusMinutes(10));
        ChatRoomMember counterpartMember = member(room, counterpartId, utcNow().minusMinutes(10));
        List<ChatMessage> roomMessages = new ArrayList<>();

        viewingUserIds.add(counterpartId);
        stubRoom(room, roomMessages, Map.of(senderId, senderMember, counterpartId, counterpartMember));
        stubSender(senderId, "sender");

        ChatMessageResponse response = chatService.sendMessage(senderId, roomId, sendRequest("hello", MessageType.TEXT));

        assertEquals(0, response.getUnreadCount());
        assertNotNull(response.getReadAt());
        assertEquals(response.getMessageId(), senderMember.getLastReadMessageId());
        assertEquals(response.getMessageId(), counterpartMember.getLastReadMessageId());
        assertEquals(1, publishedEvents.size());
        assertEquals("MESSAGE", publishedEvents.get(0).getEventType());
        assertEquals(0, publishedEvents.get(0).getUnreadCount());
    }

    @Test
    void groupRoomMessageStartsWithVisibleRecipientCount() {
        Long roomId = 12L;
        Long senderId = 1L;

        ChatRoom room = groupRoom(roomId);
        ChatRoomMember senderMember = member(room, 1L, utcNow().minusMinutes(20));
        ChatRoomMember member2 = member(room, 2L, utcNow().minusMinutes(20));
        ChatRoomMember member3 = member(room, 3L, utcNow().minusMinutes(20));
        ChatRoomMember member4 = member(room, 4L, utcNow().minusMinutes(20));
        List<ChatMessage> roomMessages = new ArrayList<>();

        stubRoom(room, roomMessages, Map.of(
                1L, senderMember,
                2L, member2,
                3L, member3,
                4L, member4
        ));
        stubSender(senderId, "sender");

        ChatMessageResponse response = chatService.sendMessage(senderId, roomId, sendRequest("group", MessageType.TEXT));

        assertEquals(3, response.getUnreadCount());
        assertNull(response.getReadAt());
        assertEquals(1, publishedEvents.size());
        assertEquals("MESSAGE", publishedEvents.get(0).getEventType());
        assertEquals(3, publishedEvents.get(0).getUnreadCount());
    }

    @Test
    void groupRoomUnreadCountDecreasesThreeTwoOneZeroAsMembersRead() {
        Long roomId = 13L;

        ChatRoom room = groupRoom(roomId);
        ChatRoomMember senderMember = member(room, 1L, utcNow().minusMinutes(20));
        ChatRoomMember member2 = member(room, 2L, utcNow().minusMinutes(20));
        ChatRoomMember member3 = member(room, 3L, utcNow().minusMinutes(20));
        ChatRoomMember member4 = member(room, 4L, utcNow().minusMinutes(20));
        senderMember.updateLastReadMessageId(700L);

        ChatMessage message = message(700L, roomId, 1L, "group", MessageType.TEXT, utcNow().minusMinutes(2));
        List<ChatMessage> roomMessages = new ArrayList<>(List.of(message));

        stubRoom(room, roomMessages, Map.of(
                1L, senderMember,
                2L, member2,
                3L, member3,
                4L, member4
        ));

        chatService.syncReadStateOnRoomViewed(roomId, 2L);
        assertEquals(2, lastPublishedUnreadCount());
        assertNull(message.getReadAt());

        chatService.syncReadStateOnRoomViewed(roomId, 3L);
        assertEquals(1, lastPublishedUnreadCount());
        assertNull(message.getReadAt());

        chatService.syncReadStateOnRoomViewed(roomId, 4L);
        assertEquals(0, lastPublishedUnreadCount());
        assertNotNull(message.getReadAt());

        assertEquals(List.of(2, 1, 0), publishedEvents.stream()
                .map(ChatMessageResponse::getUnreadCount)
                .collect(Collectors.toList()));
    }

    @Test
    void groupRoomReadReceiptsArePublishedForMessagesFromDifferentSenders() {
        Long roomId = 16L;

        ChatRoom room = groupRoom(roomId);
        ChatRoomMember senderA = member(room, 1L, utcNow().minusMinutes(20));
        ChatRoomMember senderB = member(room, 2L, utcNow().minusMinutes(20));
        ChatRoomMember reader = member(room, 3L, utcNow().minusMinutes(20));
        ChatRoomMember member4 = member(room, 4L, utcNow().minusMinutes(20));

        senderA.updateLastReadMessageId(801L);
        senderB.updateLastReadMessageId(802L);

        ChatMessage messageFromA = message(801L, roomId, 1L, "from-a", MessageType.TEXT, utcNow().minusMinutes(3));
        ChatMessage messageFromB = message(802L, roomId, 2L, "from-b", MessageType.TEXT, utcNow().minusMinutes(2));
        List<ChatMessage> roomMessages = new ArrayList<>(List.of(messageFromA, messageFromB));

        stubRoom(room, roomMessages, Map.of(
                1L, senderA,
                2L, senderB,
                3L, reader,
                4L, member4
        ));

        chatService.syncReadStateOnRoomViewed(roomId, 3L);

        assertEquals(List.of(801L, 802L), publishedEvents.stream()
                .map(ChatMessageResponse::getMessageId)
                .collect(Collectors.toList()));
        assertEquals(List.of(1, 2), publishedEvents.stream()
                .map(ChatMessageResponse::getUnreadCount)
                .collect(Collectors.toList()));
    }

    @Test
    void hiddenMemberIsExcludedFromUnreadAndNotificationTargets() {
        Long roomId = 14L;
        Long senderId = 1L;

        ChatRoom room = groupRoom(roomId);
        ChatRoomMember senderMember = member(room, 1L, utcNow().minusMinutes(30));
        ChatRoomMember member2 = member(room, 2L, utcNow().minusMinutes(30));
        ChatRoomMember member3 = member(room, 3L, utcNow().minusMinutes(30));
        ChatRoomMember hiddenMember = member(room, 4L, utcNow().minusMinutes(30));
        hiddenMember.hide("TEST");
        List<ChatMessage> roomMessages = new ArrayList<>();

        stubRoom(room, roomMessages, Map.of(
                1L, senderMember,
                2L, member2,
                3L, member3,
                4L, hiddenMember
        ));
        stubSender(senderId, "sender");
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, 4L)).thenReturn(false);

        ChatMessageResponse response = chatService.sendMessage(senderId, roomId, sendRequest("visible only", MessageType.TEXT));

        assertEquals(2, response.getUnreadCount());
        assertTrue(publishedEvents.stream().allMatch(event -> event.getUnreadCount() == 2));
        verify(notificationService, times(2)).createAndEnqueue(any());
        assertTrue(!chatService.isMember(roomId, 4L));
    }

    @Test
    void systemAndDeletedMessagesAlwaysExposeZeroUnreadCount() {
        Long roomId = 15L;
        Long senderId = 1L;

        ChatRoom room = groupRoom(roomId);
        ChatRoomMember senderMember = member(room, 1L, utcNow().minusMinutes(30));
        ChatRoomMember member2 = member(room, 2L, utcNow().minusMinutes(30));
        ChatRoomMember member3 = member(room, 3L, utcNow().minusMinutes(30));
        ChatRoomMember member4 = member(room, 4L, utcNow().minusMinutes(30));
        List<ChatMessage> roomMessages = new ArrayList<>();

        stubRoom(room, roomMessages, Map.of(
                1L, senderMember,
                2L, member2,
                3L, member3,
                4L, member4
        ));
        stubSender(senderId, "sender");

        ChatMessageResponse systemResponse = chatService.publishSystemMessage(roomId, senderId, "enter", MessageType.ENTER);
        assertEquals(0, systemResponse.getUnreadCount());
        assertEquals("MESSAGE", publishedEvents.get(0).getEventType());
        assertEquals(0, publishedEvents.get(0).getUnreadCount());

        publishedEvents.clear();
        ChatMessageResponse sent = chatService.sendMessage(senderId, roomId, sendRequest("to delete", MessageType.TEXT));
        ChatMessage savedMessage = roomMessages.stream()
                .filter(message -> savedMessageIdEquals(message, sent.getMessageId()))
                .findFirst()
                .orElseThrow();
        when(chatMessageRepository.findById(savedMessage.getId())).thenReturn(Optional.of(savedMessage));

        publishedEvents.clear();
        ChatMessageResponse deletedResponse = chatService.deleteMessage(senderId, roomId, savedMessage.getId());

        assertTrue(deletedResponse.isDeleted());
        assertEquals(0, deletedResponse.getUnreadCount());
        assertEquals("MESSAGE", publishedEvents.get(0).getEventType());
        assertEquals(0, publishedEvents.get(0).getUnreadCount());
    }

    private void stubRoom(ChatRoom room,
                          List<ChatMessage> roomMessages,
                          Map<Long, ChatRoomMember> membersByUserId) {
        Long roomId = room.getId();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.findByChatRoomIdAndHiddenAtIsNullOrderByJoinedAtAsc(roomId))
                .thenAnswer(invocation -> membersByUserId.values().stream()
                        .filter(member -> !member.isHidden())
                        .sorted((left, right) -> left.getJoinedAt().compareTo(right.getJoinedAt()))
                        .toList());
        when(chatRoomMemberRepository.findByChatRoomId(roomId))
                .thenAnswer(invocation -> new ArrayList<>(membersByUserId.values()));
        when(chatRoomMemberRepository.findUserIdsByChatRoomId(roomId))
                .thenAnswer(invocation -> new ArrayList<>(membersByUserId.keySet()));
        when(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndHiddenAtIsNull(eq(roomId), anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(membersByUserId.get(invocation.getArgument(1, Long.class)))
                        .filter(member -> !member.isHidden()));
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(eq(roomId), anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(membersByUserId.get(invocation.getArgument(1, Long.class)))
                        .filter(member -> !member.isHidden())
                        .isPresent());
        when(chatMessageRepository.findTopByRoomIdOrderByIdDesc(roomId))
                .thenAnswer(invocation -> roomMessages.stream()
                        .max((left, right) -> Long.compare(left.getId(), right.getId())));
        when(chatMessageRepository.findUnreadIncomingMessages(eq(roomId), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long userId = invocation.getArgument(1, Long.class);
                    Long lastReadMessageId = invocation.getArgument(2, Long.class);
                    ChatRoomMember member = membersByUserId.get(userId);
                    if (member == null || member.isHidden()) {
                        return Collections.emptyList();
                    }
                    return roomMessages.stream()
                            .filter(message -> !userId.equals(message.getSenderId()))
                            .filter(message -> lastReadMessageId == null || message.getId() > lastReadMessageId)
                            .filter(message -> member.getJoinedAt() == null || !member.getJoinedAt().isAfter(message.getCreatedAt()))
                            .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                            .toList();
                });
        doAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0, ChatMessage.class);
            if (message.getId() == null) {
                ReflectionTestUtils.setField(message, "id", messageIdSequence.getAndIncrement());
            }
            roomMessages.removeIf(existing -> savedMessageIdEquals(existing, message.getId()));
            roomMessages.add(message);
            return message;
        }).when(chatMessageRepository).save(any(ChatMessage.class));
    }

    private void stubSender(Long senderId, String nickname) {
        User sender = user(senderId, nickname);
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
    }

    private ChatRoom personalRoom(Long roomId, Long user1Id, Long user2Id) {
        ChatRoom room = ChatRoom.createPersonal("personal", user1Id, user2Id, null);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private ChatRoom groupRoom(Long roomId) {
        ChatRoom room = ChatRoom.create("group", ChatRoomType.GROUP);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private ChatRoomMember member(ChatRoom room, Long userId, LocalDateTime joinedAt) {
        ChatRoomMember member = ChatRoomMember.create(room, user(userId, "user-" + userId));
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);
        return member;
    }

    private User user(Long userId, String nickname) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getNickname()).thenReturn(nickname);
        when(user.getStatus()).thenReturn(null);
        when(user.getUserProfile()).thenReturn(null);
        return user;
    }

    private ChatMessage message(Long messageId,
                                Long roomId,
                                Long senderId,
                                String content,
                                MessageType messageType,
                                LocalDateTime createdAt) {
        ChatMessage message = ChatMessage.create(roomId, senderId, "sender-" + senderId, content, messageType);
        ReflectionTestUtils.setField(message, "id", messageId);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    private SendMessageRequest sendRequest(String content, MessageType messageType) {
        SendMessageRequest request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "content", content);
        ReflectionTestUtils.setField(request, "messageType", messageType);
        return request;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(java.time.Clock.systemUTC());
    }

    private int lastPublishedUnreadCount() {
        return publishedEvents.get(publishedEvents.size() - 1).getUnreadCount();
    }

    private boolean savedMessageIdEquals(ChatMessage message, Long messageId) {
        return message.getId() != null && message.getId().equals(messageId);
    }
}
