package univ.airconnect.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxDispatchServiceTest {

    private static final Long USER_ID = 11L;
    private static final Long DEVICE_ID = 21L;
    private static final Long OUTBOX_ID = 31L;
    private static final Long ROOM_ID = 41L;
    private static final Long MESSAGE_ID = 51L;

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushDeviceRepository pushDeviceRepository;
    @Mock
    private PushNotificationSender pushNotificationSender;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    private NotificationOutboxDispatchService service;

    @BeforeEach
    void setUp() {
        service = new NotificationOutboxDispatchService(
                notificationOutboxRepository,
                userRepository,
                pushDeviceRepository,
                pushNotificationSender,
                chatRoomRepository,
                chatRoomMemberRepository,
                chatMessageRepository
        );
    }

    @Test
    void sendsOnlyWhenCurrentRecipientDeviceOwnershipStillMatches() {
        NotificationOutbox outbox = processingOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice device = device(USER_ID, "token-1");
        stubLockedRows(outbox, user, device);
        when(pushNotificationSender.send(outbox))
                .thenReturn(PushNotificationSender.PushSendResult.success("fcm-message-1"));

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender).send(outbox);
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
    }

    @Test
    void logoutAfterOutboxCreationSkipsPreviouslyClaimedPush() {
        NotificationOutbox outbox = processingOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice device = device(USER_ID, "token-1");
        device.deactivate();
        stubLockedRows(outbox, user, device);

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        assertSkipped(outbox, NotificationOutboxDispatchService.PUSH_DEVICE_INACTIVE);
    }

    @Test
    void accountSwitchWithSameTokenSkipsPreviousUsersOutbox() {
        NotificationOutbox outbox = processingOutbox("shared-token");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice previousOwnerDevice = device(USER_ID, "shared-token");
        previousOwnerDevice.releaseTokenOwnership();
        stubLockedRows(outbox, user, previousOwnerDevice);

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        assertSkipped(outbox, NotificationOutboxDispatchService.PUSH_DEVICE_INACTIVE);
    }

    @Test
    void deletedAccountSkipsPreviouslyClaimedOutbox() {
        NotificationOutbox outbox = processingOutbox("token-1");
        User deletedUser = user(USER_ID, UserStatus.DELETED);
        when(notificationOutboxRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(outbox));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(deletedUser));

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        verify(pushDeviceRepository, never()).findByIdForUpdate(DEVICE_ID);
        assertSkipped(outbox, NotificationOutboxDispatchService.RECIPIENT_NOT_ACTIVE);
    }

    @Test
    void tokenRefreshSkipsOutboxContainingPreviousToken() {
        NotificationOutbox outbox = processingOutbox("token-old");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice device = device(USER_ID, "token-old");
        device.refreshToken(
                "token-new", null, true, "1.0.1", "15", "ko-KR", "Asia/Seoul", LocalDateTime.now());
        stubLockedRows(outbox, user, device);

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        assertSkipped(outbox, NotificationOutboxDispatchService.PUSH_TOKEN_CHANGED);
    }

    @Test
    void revokedNotificationPermissionSkipsOutbox() {
        NotificationOutbox outbox = processingOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice device = device(USER_ID, "token-1");
        device.updatePermission(false);
        stubLockedRows(outbox, user, device);

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        assertSkipped(outbox, NotificationOutboxDispatchService.PUSH_PERMISSION_REVOKED);
    }

    @Test
    void changedDeviceOwnerSkipsOutbox() {
        NotificationOutbox outbox = processingOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice device = device(99L, "token-1");
        stubLockedRows(outbox, user, device);

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        assertSkipped(outbox, NotificationOutboxDispatchService.PUSH_DEVICE_OWNER_CHANGED);
    }

    @Test
    void invalidTokenResponseReleasesOnlyTheStillValidatedDevice() {
        NotificationOutbox outbox = processingOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice device = device(USER_ID, "token-1");
        stubLockedRows(outbox, user, device);
        when(pushNotificationSender.send(outbox))
                .thenReturn(PushNotificationSender.PushSendResult.invalidToken("UNREGISTERED", "gone"));

        service.dispatch(OUTBOX_ID);

        assertThat(device.getActive()).isFalse();
        assertThat(device.getPushToken()).startsWith("released:");
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.SKIPPED);
    }

    @Test
    void invalidArgumentPayloadFailureKeepsValidatedTokenAndFailsOutbox() {
        NotificationOutbox outbox = processingOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        PushDevice device = device(USER_ID, "token-1");
        stubLockedRows(outbox, user, device);
        when(pushNotificationSender.send(outbox))
                .thenReturn(PushNotificationSender.PushSendResult.failed(
                        "INVALID_ARGUMENT", "payload contains an invalid data key"));

        service.dispatch(OUTBOX_ID);

        assertThat(device.getActive()).isTrue();
        assertThat(device.getPushToken()).isEqualTo("token-1");
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(outbox.getLastErrorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void chatPushIsSkippedWhenRecipientHasLeftRoom() {
        NotificationOutbox outbox = processingChatOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        when(notificationOutboxRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(outbox));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(chatRoomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room()));
        when(chatRoomMemberRepository.findVisibleByChatRoomIdAndUserIdForUpdate(ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        verify(pushDeviceRepository, never()).findByIdForUpdate(DEVICE_ID);
        assertSkipped(outbox, NotificationOutboxDispatchService.CHAT_ROOM_ACCESS_REVOKED);
    }

    @Test
    void chatPushIsSkippedWhenMessageWasSoftDeleted() {
        NotificationOutbox outbox = processingChatOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        ChatRoom room = room();
        ChatRoomMember membership = ChatRoomMember.create(room, user);
        ChatMessage message = message();
        message.softDelete();
        stubChatRows(outbox, user, room, membership, message);

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender, never()).send(outbox);
        verify(pushDeviceRepository, never()).findByIdForUpdate(DEVICE_ID);
        assertSkipped(outbox, NotificationOutboxDispatchService.CHAT_MESSAGE_NOT_AVAILABLE);
    }

    @Test
    void validChatPushStillSends() {
        NotificationOutbox outbox = processingChatOutbox("token-1");
        User user = user(USER_ID, UserStatus.ACTIVE);
        ChatRoom room = room();
        ChatRoomMember membership = ChatRoomMember.create(room, user);
        ChatMessage message = message();
        PushDevice device = device(USER_ID, "token-1");
        stubChatRows(outbox, user, room, membership, message);
        when(pushDeviceRepository.findByIdForUpdate(DEVICE_ID)).thenReturn(Optional.of(device));
        when(pushNotificationSender.send(outbox))
                .thenReturn(PushNotificationSender.PushSendResult.success("fcm-chat-1"));

        service.dispatch(OUTBOX_ID);

        verify(pushNotificationSender).send(outbox);
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
    }

    private void stubLockedRows(NotificationOutbox outbox, User user, PushDevice device) {
        when(notificationOutboxRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(outbox));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(pushDeviceRepository.findByIdForUpdate(DEVICE_ID)).thenReturn(Optional.of(device));
    }

    private NotificationOutbox processingOutbox(String token) {
        NotificationOutbox outbox = NotificationOutbox.create(
                101L,
                USER_ID,
                DEVICE_ID,
                PushProvider.FCM,
                token,
                "알림 제목",
                "알림 본문",
                "{\"notificationType\":\"MATCH_REQUEST_RECEIVED\"}",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(outbox, "id", OUTBOX_ID);
        outbox.claim();
        return outbox;
    }

    private NotificationOutbox processingChatOutbox(String token) {
        NotificationOutbox outbox = NotificationOutbox.create(
                102L,
                USER_ID,
                DEVICE_ID,
                PushProvider.FCM,
                token,
                "채팅 알림",
                "새 메시지",
                "{\"notificationType\":\"CHAT_MESSAGE_RECEIVED\",\"chatRoomId\":\"" + ROOM_ID
                        + "\",\"messageId\":\"" + MESSAGE_ID + "\"}",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(outbox, "id", OUTBOX_ID);
        outbox.claim();
        return outbox;
    }

    private void stubChatRows(NotificationOutbox outbox,
                              User user,
                              ChatRoom room,
                              ChatRoomMember membership,
                              ChatMessage message) {
        when(notificationOutboxRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(outbox));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(chatRoomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.findVisibleByChatRoomIdAndUserIdForUpdate(ROOM_ID, USER_ID))
                .thenReturn(Optional.of(membership));
        when(chatMessageRepository.findByIdForUpdate(MESSAGE_ID)).thenReturn(Optional.of(message));
    }

    private ChatRoom room() {
        ChatRoom room = ChatRoom.create("chat", ChatRoomType.PERSONAL);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    private ChatMessage message() {
        ChatMessage message = ChatMessage.create(ROOM_ID, 99L, "sender", "hello", MessageType.TEXT);
        ReflectionTestUtils.setField(message, "id", MESSAGE_ID);
        return message;
    }

    private User user(Long userId, UserStatus status) {
        User user = User.create(SocialProvider.KAKAO, "social-" + userId, "u" + userId + "@test.local");
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private PushDevice device(Long userId, String token) {
        PushDevice device = PushDevice.register(
                userId,
                "device-1",
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
        );
        ReflectionTestUtils.setField(device, "id", DEVICE_ID);
        return device;
    }

    private void assertSkipped(NotificationOutbox outbox, String reason) {
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.SKIPPED);
        assertThat(outbox.getLastErrorCode()).isEqualTo(reason);
    }
}
