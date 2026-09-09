package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;

/**
 * outbox 발송 직전에 현재 사용자와 푸시 디바이스 소유권을 다시 검증한다.
 *
 * <p>사용자와 디바이스 행의 쓰기 잠금을 FCM 응답까지 유지해 로그아웃, 계정 전환,
 * 토큰 갱신, 회원 탈퇴와 발송이 서로 엇갈리지 않게 한다.</p>
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

    private static final int MAX_ATTEMPTS = 3;

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final UserRepository userRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushNotificationSender pushNotificationSender;

    /**
     * PROCESSING outbox 한 건을 잠근 뒤 현재 수신 자격을 검증하고 발송한다.
     */
    @Transactional
    public void dispatch(Long outboxId) {
        NotificationOutbox outbox = notificationOutboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null) {
            log.debug("Ignoring deleted notification outbox: outboxId={}", outboxId);
            return;
        }

        if (outbox.getStatus() != NotificationDeliveryStatus.PROCESSING) {
            log.debug("Ignoring outbox outside PROCESSING state: outboxId={}, status={}",
                    outboxId, outbox.getStatus());
            return;
        }

        User recipient = userRepository.findByIdForUpdate(outbox.getUserId()).orElse(null);
        if (recipient == null || recipient.getStatus() != UserStatus.ACTIVE) {
            skip(outbox, RECIPIENT_NOT_ACTIVE, "수신 사용자가 없거나 활성 상태가 아닙니다.");
            return;
        }

        PushDevice device = pushDeviceRepository.findByIdForUpdate(outbox.getPushDeviceId()).orElse(null);
        String invalidReason = validateCurrentDevice(outbox, device);
        if (invalidReason != null) {
            skip(outbox, invalidReason, "현재 푸시 디바이스 상태가 outbox 생성 시점과 다릅니다.");
            return;
        }

        sendLocked(outbox, device);
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

    private void sendLocked(NotificationOutbox outbox, PushDevice device) {
        try {
            PushNotificationSender.PushSendResult result = pushNotificationSender.send(outbox);
            if (result.success()) {
                outbox.markSent(result.providerMessageId());
                return;
            }

            if (result.invalidToken()) {
                device.releaseTokenOwnership();
                outbox.markSkipped(result.errorCode(), result.errorMessage());
                return;
            }

            if (result.retryable() && canRetry(outbox)) {
                outbox.markRetry(result.errorCode(), result.errorMessage(), nextAttemptAt(outbox));
                return;
            }

            outbox.markFailed(result.errorCode(), result.errorMessage());
        } catch (Exception e) {
            log.error("Notification outbox dispatch failed unexpectedly: outboxId={}", outbox.getId(), e);
            if (canRetry(outbox)) {
                outbox.markRetry("UNEXPECTED_ERROR", e.getMessage(), nextAttemptAt(outbox));
                return;
            }
            outbox.markFailed("UNEXPECTED_ERROR", e.getMessage());
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
