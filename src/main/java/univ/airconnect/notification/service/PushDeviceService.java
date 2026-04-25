package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 운영용 푸시 디바이스 등록과 생명주기 관리를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;

    /**
     * userId와 deviceId 기준으로 논리 디바이스 행을 upsert한다.
     */
    @Transactional
    public PushDevice registerOrUpdate(UpsertCommand command) {
        validate(command);
        reassignTokenOwnerIfNecessary(command);

        String normalizedAppVersion = trimToLength(command.appVersion(), 50);
        String normalizedOsVersion = trimToLength(command.osVersion(), 50);
        String normalizedLocale = trimToLength(command.locale(), 20);
        String normalizedTimezone = trimToLength(command.timezone(), 50);

        Optional<PushDevice> existing = pushDeviceRepository.findByUserIdAndDeviceId(command.userId(), command.deviceId());
        if (existing.isPresent()) {
            PushDevice pushDevice = existing.get();
            validateExistingDeviceIdentity(pushDevice, command);
            pushDevice.refreshToken(
                    command.pushToken(),
                    command.apnsToken(),
                    command.notificationPermissionGranted(),
                    normalizedAppVersion,
                    normalizedOsVersion,
                    normalizedLocale,
                    normalizedTimezone,
                    command.lastSeenAt()
            );
            log.info("Push device updated: userId={}, deviceId={}", command.userId(), command.deviceId());
            return pushDevice;
        }

        PushDevice pushDevice = pushDeviceRepository.save(
                PushDevice.register(
                        command.userId(),
                        command.deviceId(),
                        command.platform(),
                        command.provider(),
                        command.pushToken(),
                        command.apnsToken(),
                        command.notificationPermissionGranted(),
                        normalizedAppVersion,
                        normalizedOsVersion,
                        normalizedLocale,
                        normalizedTimezone,
                        command.lastSeenAt()
                )
        );
        log.info("Push device registered: userId={}, deviceId={}", command.userId(), command.deviceId());
        return pushDevice;
    }

    private void validateExistingDeviceIdentity(PushDevice existing, UpsertCommand command) {
        if (existing.getPlatform() != command.platform()) {
            throw new IllegalArgumentException("Platform mismatch for existing device.");
        }
        if (existing.getProvider() != command.provider()) {
            throw new IllegalArgumentException("Provider mismatch for existing device.");
        }
    }

    /**
     * 주어진 사용자의 디바이스 한 건을 비활성화한다.
     */
    @Transactional
    public void deactivate(Long userId, String deviceId) {
        PushDevice pushDevice = getRequiredDevice(userId, deviceId);
        pushDevice.deactivate();
        log.info("Push device deactivated: userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * FCM이 토큰을 무효라고 보고하면 해당 토큰을 비활성화한다.
     */
    @Transactional
    public void deactivateInvalidToken(PushProvider provider, String pushToken) {
        pushDeviceRepository.findByProviderAndPushToken(provider, pushToken)
                .ifPresent(pushDevice -> {
                    pushDevice.releaseTokenOwnership();
                    log.warn("Push token released after provider failure: deviceId={}, provider={}",
                            pushDevice.getDeviceId(), provider);
                });
    }

    /**
     * 현재 푸시 수신이 가능한 활성 디바이스 목록을 반환한다.
     */
    public List<PushDevice> findPushableDevices(Long userId) {
        return pushDeviceRepository.findByUserIdAndActiveTrueAndNotificationPermissionGrantedTrue(userId);
    }

    /**
     * 디바이스 관리 화면에 보여줄 활성 디바이스 목록을 반환한다.
     */
    public List<PushDevice> findActiveDevices(Long userId) {
        return pushDeviceRepository.findByUserIdAndActiveTrue(userId);
    }

    /**
     * OS 알림 권한과 마지막 접속 시각만 수정한다.
     */
    @Transactional
    public PushDevice updatePermission(Long userId, String deviceId, boolean granted, LocalDateTime lastSeenAt) {
        PushDevice pushDevice = getRequiredDevice(userId, deviceId);
        pushDevice.updatePermission(granted);
        pushDevice.touchLastSeen(lastSeenAt);
        log.info("Push device permission updated: userId={}, deviceId={}, granted={}", userId, deviceId, granted);
        return pushDevice;
    }

    private void reassignTokenOwnerIfNecessary(UpsertCommand command) {
        pushDeviceRepository.findByProviderAndPushToken(command.provider(), command.pushToken())
                .ifPresent(existingTokenOwner -> {
                    boolean sameOwner = existingTokenOwner.getUserId().equals(command.userId())
                            && existingTokenOwner.getDeviceId().equals(command.deviceId());
                    if (sameOwner) {
                        return;
                    }
                    existingTokenOwner.releaseTokenOwnership();
                    log.info("Push token ownership transferred: oldUserId={}, oldDeviceId={}, newUserId={}, newDeviceId={}",
                            existingTokenOwner.getUserId(), existingTokenOwner.getDeviceId(),
                            command.userId(), command.deviceId());
                });
    }

    private PushDevice getRequiredDevice(Long userId, String deviceId) {
        return pushDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "등록된 푸시 디바이스를 찾을 수 없습니다."
                ));
    }

    private void validate(UpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("요청 명령은 필수입니다.");
        }
        if (command.userId() == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (command.deviceId() == null || command.deviceId().isBlank()) {
            throw new IllegalArgumentException("디바이스 ID는 필수입니다.");
        }
        if (command.platform() == null) {
            throw new IllegalArgumentException("플랫폼은 필수입니다.");
        }
        if (command.provider() == null) {
            throw new IllegalArgumentException("푸시 제공자는 필수입니다.");
        }
        if (command.provider() != PushProvider.FCM) {
            throw new IllegalArgumentException("현재는 FCM 제공자만 지원합니다.");
        }
        if (command.pushToken() == null || command.pushToken().isBlank()) {
            throw new IllegalArgumentException("푸시 토큰은 필수입니다.");
        }
        if (command.notificationPermissionGranted() == null) {
            throw new IllegalArgumentException("알림 권한 허용 여부는 필수입니다.");
        }
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    public record UpsertCommand(
            Long userId,
            String deviceId,
            PushPlatform platform,
            PushProvider provider,
            String pushToken,
            String apnsToken,
            Boolean notificationPermissionGranted,
            String appVersion,
            String osVersion,
            String locale,
            String timezone,
            LocalDateTime lastSeenAt
    ) {
    }
}
