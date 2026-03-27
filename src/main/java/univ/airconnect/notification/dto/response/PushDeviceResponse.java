package univ.airconnect.notification.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.PushTokenStatus;
import univ.airconnect.notification.domain.entity.PushDevice;

import java.time.LocalDateTime;

/**
 * 등록된 푸시 디바이스 한 건의 응답 모델이다.
 */
@Getter
@Builder
public class PushDeviceResponse {

    private Long pushDeviceId;
    private Long userId;
    private String deviceId;
    private PushPlatform platform;
    private PushProvider provider;
    private PushTokenStatus tokenStatus;
    private Boolean pushEnabled;
    private Boolean notificationPermissionGranted;
    private Boolean active;
    private Boolean apnsTokenRegistered;
    private String appVersion;
    private String osVersion;
    private String locale;
    private String timezone;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastTokenRefreshedAt;
    private LocalDateTime deactivatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PushDeviceResponse from(PushDevice pushDevice) {
        return PushDeviceResponse.builder()
                .pushDeviceId(pushDevice.getId())
                .userId(pushDevice.getUserId())
                .deviceId(pushDevice.getDeviceId())
                .platform(pushDevice.getPlatform())
                .provider(pushDevice.getProvider())
                .tokenStatus(Boolean.TRUE.equals(pushDevice.getActive())
                        ? PushTokenStatus.ACTIVE
                        : PushTokenStatus.INACTIVE)
                .pushEnabled(pushDevice.getNotificationPermissionGranted())
                .notificationPermissionGranted(pushDevice.getNotificationPermissionGranted())
                .active(pushDevice.getActive())
                .apnsTokenRegistered(pushDevice.getApnsToken() != null && !pushDevice.getApnsToken().isBlank())
                .appVersion(pushDevice.getAppVersion())
                .osVersion(pushDevice.getOsVersion())
                .locale(pushDevice.getLocale())
                .timezone(pushDevice.getTimezone())
                .lastSeenAt(pushDevice.getLastSeenAt())
                .lastTokenRefreshedAt(pushDevice.getLastTokenRefreshedAt())
                .deactivatedAt(pushDevice.getDeactivatedAt())
                .createdAt(pushDevice.getCreatedAt())
                .updatedAt(pushDevice.getUpdatedAt())
                .build();
    }
}
