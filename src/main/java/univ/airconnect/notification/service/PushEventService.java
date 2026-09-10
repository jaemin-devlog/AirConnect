package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.notification.domain.entity.PushEvent;
import univ.airconnect.notification.repository.PushEventRepository;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;

import java.time.LocalDateTime;

/**
 * 클라이언트가 보고한 푸시 수신/열람 추적 이벤트를 저장한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushEventService {

    private final PushEventRepository pushEventRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;

    @Transactional
    public PushEvent create(Long userId, CreateCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "푸시 이벤트 요청 본문이 필요합니다.");
        }

        Long notificationId = parseNotificationId(command.notificationId());
        // 삭제한 알림도 지연 도착한 수신/열람 기록은 허용하되 소유권은 확인한다.
        if (!notificationRepository.existsByIdAndUserId(notificationId, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다.");
        }
        if (command.deviceId() == null || command.deviceId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "디바이스 ID는 필수입니다.");
        }
        // 계정 전환과 이벤트 저장이 엇갈리지 않도록 기기 소유권을 잠금 상태에서 확인한다.
        pushDeviceRepository.findByUserIdAndDeviceIdForUpdate(userId, command.deviceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "등록된 디바이스를 찾을 수 없습니다."));
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
            long notificationId = Long.parseLong(rawNotificationId);
            if (notificationId <= 0) {
                throw new NumberFormatException();
            }
            return notificationId;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알림 ID는 숫자 문자열이어야 합니다.");
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
