package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;

/**
 * outbox 발송 직전에 현재 사용자와 푸시 디바이스 소유권을 다시 검증한다.
 *
 * Starts the asynchronous provider call while authorization locks are held, then
 * releases DB locks before awaiting the provider response. Completion is fenced per attempt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxDispatchService {

    static final String RECIPIENT_NOT_ACTIVE = "RECIPIENT_NOT_ACTIVE";
    static final String PUSH_DEVICE_NOT_FOUND = "PUSH_DEVICE_NOT_FOUND";
    static final String PUSH_DEVICE_INACTIVE = "PUSH_DEVICE_INACTIVE";
    static final String PUSH_PERMISSION_REVOKED = "PUSH_PERMISSION_REVOKED";
    static final String PUSH_DEVICE_OWNER_CHANGED = "PUSH_DEVICE_OWNER_CHANGED";
    static final String PUSH_PROVIDER_CHANGED = "PUSH_PROVIDER_CHANGED";
    static final String PUSH_TOKEN_CHANGED = "PUSH_TOKEN_CHANGED";
    static final String CHAT_ROOM_ACCESS_REVOKED = "CHAT_ROOM_ACCESS_REVOKED";
    static final String CHAT_MESSAGE_NOT_AVAILABLE = "CHAT_MESSAGE_NOT_AVAILABLE";
    static final String CHAT_PUSH_PAYLOAD_INVALID = "CHAT_PUSH_PAYLOAD_INVALID";

    private static final int MAX_ATTEMPTS = 3;
    private static final String CHAT_NOTIFICATION_TYPE = "CHAT_MESSAGE_RECEIVED";
    private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper();

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final UserRepository userRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushNotificationSender pushNotificationSender;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private record Attempt(Long id, String token, Long userId, Long deviceId, String targetToken,
                           CompletableFuture<PushNotificationSender.PushSendResult> future) {}


    /**
     * PROCESSING outbox 한 건을 잠근 뒤 현재 수신 자격을 검증하고 발송한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void dispatch(Long outboxId) {
        Attempt attempt = newTransaction().execute(status -> begin(outboxId));
        if (attempt == null) return;
        PushNotificationSender.PushSendResult result;
        try {
            // No application transaction/row lock is held while waiting for FCM.
            result = attempt.future().get(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result = PushNotificationSender.PushSendResult.retryableFailure("INTERRUPTED", "Push wait interrupted");
        } catch (Exception failure) {
            result = PushNotificationSender.PushSendResult.retryableFailure("PROVIDER_FAILURE", failure.toString());
        }
        var completedResult = result;
        newTransaction().executeWithoutResult(status -> complete(attempt, completedResult));
    }

    private Attempt begin(Long outboxId) {
        NotificationOutbox outbox = notificationOutboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null) {
            log.debug("Ignoring deleted notification outbox: outboxId={}", outboxId);
            return null;
        }

        if (outbox.getStatus() != NotificationDeliveryStatus.PROCESSING) {
            log.debug("Ignoring outbox outside PROCESSING state: outboxId={}, status={}",
                    outboxId, outbox.getStatus());
            return null;
        }

        User recipient = userRepository.findByIdForUpdate(outbox.getUserId()).orElse(null);
        if (recipient == null || recipient.getStatus() != UserStatus.ACTIVE) {
            skip(outbox, RECIPIENT_NOT_ACTIVE, "수신 사용자가 없거나 활성 상태가 아닙니다.");
            return null;
        }

        String chatInvalidReason = validateCurrentChatDelivery(outbox);
        if (chatInvalidReason != null) {
            skip(outbox, chatInvalidReason, "현재 채팅방 또는 메시지 상태가 발송 조건을 만족하지 않습니다.");
            return null;
        }

        PushDevice device = pushDeviceRepository.findByIdForUpdate(outbox.getPushDeviceId()).orElse(null);
        String invalidReason = validateCurrentDevice(outbox, device);
        if (invalidReason != null) {
            skip(outbox, invalidReason, "현재 푸시 디바이스 상태가 outbox 생성 시점과 다릅니다.");
            return null;
        }

        String token = outbox.beginDispatch();
        if (token == null) return null;
        CompletableFuture<PushNotificationSender.PushSendResult> future;
        try {
            future = pushNotificationSender.sendAsync(outbox, device.getPlatform());
        } catch (RuntimeException failure) {
            future = CompletableFuture.failedFuture(failure);
        }
        return new Attempt(outbox.getId(), token, outbox.getUserId(), device.getId(), outbox.getTargetToken(), future);
    }

    private String validateCurrentChatDelivery(NotificationOutbox outbox) {
        JsonNode payload;
        try {
            payload = PAYLOAD_MAPPER.readTree(outbox.getDataJson());
        } catch (Exception exception) {
            log.warn("Unable to parse outbox payload before dispatch: outboxId={}", outbox.getId());
            return null;
        }

        if (payload == null || !CHAT_NOTIFICATION_TYPE.equals(payload.path("notificationType").asText())) {
            return null;
        }

        Long roomId = positiveLong(payload.get("chatRoomId"));
        Long messageId = positiveLong(payload.get("messageId"));
        if (roomId == null || messageId == null) {
            return CHAT_PUSH_PAYLOAD_INVALID;
        }

        if (chatRoomRepository.findByIdForUpdate(roomId).isEmpty()) {
            return CHAT_ROOM_ACCESS_REVOKED;
        }

        ChatRoomMember membership = chatRoomMemberRepository
                .findVisibleByChatRoomIdAndUserIdForUpdate(roomId, outbox.getUserId())
                .orElse(null);
        if (membership == null) {
            return CHAT_ROOM_ACCESS_REVOKED;
        }

        ChatMessage message = chatMessageRepository.findByIdForUpdate(messageId).orElse(null);
        if (message == null || message.isDeleted() || !roomId.equals(message.getRoomId())) {
            return CHAT_MESSAGE_NOT_AVAILABLE;
        }
        if (membership.getJoinedAt() != null
                && message.getCreatedAt() != null
                && message.getCreatedAt().isBefore(membership.getJoinedAt())) {
            return CHAT_ROOM_ACCESS_REVOKED;
        }
        return null;
    }

    private Long positiveLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            long value = Long.parseLong(node.asText());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String validateCurrentDevice(NotificationOutbox outbox, PushDevice device) {
        if (device == null) {
            return PUSH_DEVICE_NOT_FOUND;
        }
        if (!Boolean.TRUE.equals(device.getActive())) {
            return PUSH_DEVICE_INACTIVE;
        }
        if (!Boolean.TRUE.equals(device.getNotificationPermissionGranted())) {
            return PUSH_PERMISSION_REVOKED;
        }
        if (!outbox.getUserId().equals(device.getUserId())) {
            return PUSH_DEVICE_OWNER_CHANGED;
        }
        if (outbox.getProvider() != device.getProvider()) {
            return PUSH_PROVIDER_CHANGED;
        }
        if (!outbox.getTargetToken().equals(device.getPushToken())) {
            return PUSH_TOKEN_CHANGED;
        }
        return null;
    }

    private void complete(Attempt attempt, PushNotificationSender.PushSendResult result) {
        var outbox = notificationOutboxRepository.findByIdForUpdate(attempt.id()).orElse(null);
        if (outbox == null || outbox.getStatus() != NotificationDeliveryStatus.PROCESSING
                || !attempt.token().equals(outbox.getDispatchToken())) return;
        if (result.success()) {
            outbox.markSent(result.providerMessageId());
        } else if (result.invalidToken()) {
            // A late invalid-token response must not invalidate a refreshed/reassigned device.
            pushDeviceRepository.findByIdForUpdate(attempt.deviceId()).ifPresent(device -> {
                if (attempt.userId().equals(device.getUserId())
                        && attempt.targetToken().equals(device.getPushToken())) device.releaseTokenOwnership();
            });
            outbox.markSkipped(result.errorCode(), result.errorMessage());
        } else if (result.retryable() && canRetry(outbox)) {
            outbox.markRetry(result.errorCode(), result.errorMessage(), nextAttemptAt(outbox));
        } else {
            outbox.markFailed(result.errorCode(), result.errorMessage());
        }
    }

    private void skip(NotificationOutbox outbox, String errorCode, String errorMessage) {
        outbox.markSkipped(errorCode, errorMessage);
        log.info("Notification outbox skipped after recipient revalidation: outboxId={}, reason={}",
                outbox.getId(), errorCode);
    }

    private boolean canRetry(NotificationOutbox outbox) {
        return outbox.getAttemptCount() + 1 < MAX_ATTEMPTS;
    }

    private LocalDateTime nextAttemptAt(NotificationOutbox outbox) {
        int nextAttemptNumber = outbox.getAttemptCount() + 1;
        return switch (nextAttemptNumber) {
            case 1 -> LocalDateTime.now().plusMinutes(1);
            case 2 -> LocalDateTime.now().plusMinutes(5);
            default -> LocalDateTime.now().plusMinutes(30);
        };
    }
}
