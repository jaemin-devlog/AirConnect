package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.domain.entity.PushEvent;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.repository.PushEventRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushEventService {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushEventRepository pushEventRepository;

    @Transactional
    public PushEvent create(Long userId, CreateCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Push event request body is required.");
        }

        Long notificationId = parseNotificationId(command.notificationId());
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Notification not found for user."));
        PushDevice pushDevice = pushDeviceRepository.findByUserIdAndDeviceId(userId, command.deviceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Push device not found for user."));

        Optional<PushEvent> existing = pushEventRepository.findTopByUserIdAndNotificationIdAndEventTypeAndDeviceIdOrderByIdDesc(
                userId,
                notificationId,
                command.eventType(),
                command.deviceId()
        );
        if (existing.isPresent()) {
            log.debug("Duplicate push event ignored: userId={}, notificationId={}, eventType={}, deviceId={}",
                    userId, notificationId, command.eventType(), command.deviceId());
            return existing.get();
        }

        validateOutboxLink(notification, pushDevice, command.providerMessageId());

        return pushEventRepository.save(
                PushEvent.create(
                        userId,
                        notificationId,
                        normalizeProviderMessageId(command.providerMessageId()),
                        command.eventType(),
                        command.occurredAt(),
                        command.deviceId()
                )
        );
    }

    private Long parseNotificationId(String rawNotificationId) {
        try {
            return Long.parseLong(rawNotificationId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification id must be numeric.");
        }
    }

    private void validateOutboxLink(Notification notification,
                                    PushDevice pushDevice,
                                    String providerMessageId) {
        Optional<NotificationOutbox> linkedOutbox = hasText(providerMessageId)
                ? notificationOutboxRepository.findTopByNotificationIdAndPushDeviceIdAndProviderMessageIdAndStatusOrderByIdDesc(
                        notification.getId(),
                        pushDevice.getId(),
                        providerMessageId.trim(),
                        NotificationDeliveryStatus.SENT
                )
                : notificationOutboxRepository.findTopByNotificationIdAndPushDeviceIdAndStatusOrderByIdDesc(
                        notification.getId(),
                        pushDevice.getId(),
                        NotificationDeliveryStatus.SENT
                );

        if (linkedOutbox.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Push event does not match a sent notification delivery.");
        }
    }

    private String normalizeProviderMessageId(String providerMessageId) {
        if (!hasText(providerMessageId)) {
            return null;
        }
        return providerMessageId.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record CreateCommand(
            String notificationId,
            String providerMessageId,
            univ.airconnect.notification.domain.PushEventType eventType,
            LocalDateTime occurredAt,
            String deviceId
    ) {
    }
}
