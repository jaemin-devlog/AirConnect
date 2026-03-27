package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.notification.domain.entity.PushEvent;
import univ.airconnect.notification.repository.PushEventRepository;

import java.time.LocalDateTime;

/**
 * 클라이언트가 보고한 푸시 수신/열람 추적 이벤트를 저장한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushEventService {

    private final PushEventRepository pushEventRepository;

    @Transactional
    public PushEvent create(Long userId, CreateCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Push event request body is required.");
        }

        Long notificationId = parseNotificationId(command.notificationId());
        PushEvent pushEvent = pushEventRepository.save(
                PushEvent.create(
                        userId,
                        notificationId,
                        command.providerMessageId(),
                        command.eventType(),
                        command.occurredAt(),
                        command.deviceId()
                )
        );
        return pushEvent;
    }

    private Long parseNotificationId(String rawNotificationId) {
        try {
            return Long.parseLong(rawNotificationId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "notificationId must be a numeric string.");
        }
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
