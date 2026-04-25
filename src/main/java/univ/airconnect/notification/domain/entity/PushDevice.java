package univ.airconnect.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 한 명의 모바일 디바이스 한 대와 현재 FCM 토큰을 저장한다.
 */
@Entity
@Table(
        name = "push_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_push_devices_user_device", columnNames = {"user_id", "device_id"}),
                @UniqueConstraint(name = "uk_push_devices_provider_token", columnNames = {"provider", "push_token"})
        },
        indexes = {
                @Index(name = "idx_push_devices_user_active", columnList = "user_id, active"),
                @Index(name = "idx_push_devices_token_refreshed", columnList = "last_token_refreshed_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushProvider provider;

    @Column(name = "push_token", nullable = false, length = 512)
    private String pushToken;

    @Column(name = "apns_token", length = 512)
    private String apnsToken;

    @Column(name = "notification_permission_granted", nullable = false)
    private Boolean notificationPermissionGranted;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(length = 20)
    private String locale;

    @Column(length = 50)
    private String timezone;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "last_token_refreshed_at", nullable = false)
    private LocalDateTime lastTokenRefreshedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private PushDevice(Long userId,
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
                       LocalDateTime lastSeenAt) {
        validate(userId, deviceId, platform, provider, pushToken, apnsToken, notificationPermissionGranted);
        this.userId = userId;
        this.deviceId = deviceId;
        this.platform = platform;
        this.provider = provider;
        this.pushToken = pushToken;
        this.apnsToken = apnsToken;
        this.notificationPermissionGranted = notificationPermissionGranted;
        this.active = Boolean.TRUE;
        this.appVersion = appVersion;
        this.osVersion = osVersion;
        this.locale = locale;
        this.timezone = timezone;
        this.lastSeenAt = lastSeenAt;
        this.lastTokenRefreshedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static PushDevice register(Long userId,
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
                                      LocalDateTime lastSeenAt) {
        return PushDevice.builder()
                .userId(userId)
                .deviceId(deviceId)
                .platform(platform)
                .provider(provider)
                .pushToken(pushToken)
                .apnsToken(apnsToken)
                .notificationPermissionGranted(notificationPermissionGranted)
                .appVersion(appVersion)
                .osVersion(osVersion)
                .locale(locale)
                .timezone(timezone)
                .lastSeenAt(lastSeenAt)
                .build();
    }

    public void refreshToken(String pushToken,
                             String apnsToken,
                             Boolean notificationPermissionGranted,
                             String appVersion,
                             String osVersion,
                             String locale,
                             String timezone,
                             LocalDateTime lastSeenAt) {
        if (pushToken == null || pushToken.isBlank()) {
            throw new IllegalArgumentException("푸시 토큰은 필수입니다.");
        }
        if (this.platform == PushPlatform.ANDROID && apnsToken != null && !apnsToken.isBlank()) {
            throw new IllegalArgumentException("안드로이드에서는 APNS 토큰을 사용할 수 없습니다.");
        }
        this.pushToken = pushToken;
        this.apnsToken = apnsToken;
        if (notificationPermissionGranted != null) {
            this.notificationPermissionGranted = notificationPermissionGranted;
        }
        this.appVersion = appVersion;
        this.osVersion = osVersion;
        this.locale = locale;
        this.timezone = timezone;
        this.lastSeenAt = lastSeenAt;
        this.lastTokenRefreshedAt = LocalDateTime.now();
        this.active = Boolean.TRUE;
        this.deactivatedAt = null;
        touch();
    }

    public void touchLastSeen(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt != null ? lastSeenAt : LocalDateTime.now();
        touch();
    }

    public void updatePermission(boolean granted) {
        this.notificationPermissionGranted = granted;
        touch();
    }

    public void deactivate() {
        if (!Boolean.TRUE.equals(this.active)) {
            return;
        }
        this.active = Boolean.FALSE;
        this.deactivatedAt = LocalDateTime.now();
        touch();
    }

    public void releaseTokenOwnership() {
        this.active = Boolean.FALSE;
        this.deactivatedAt = LocalDateTime.now();
        this.pushToken = buildReleasedTokenValue();
        this.apnsToken = null;
        this.notificationPermissionGranted = Boolean.FALSE;
        touch();
    }

    public void reactivate() {
        this.active = Boolean.TRUE;
        this.deactivatedAt = null;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    private String buildReleasedTokenValue() {
        return "released:" + this.deviceId + ":" + UUID.randomUUID();
    }

    private void validate(Long userId,
                          String deviceId,
                          PushPlatform platform,
                          PushProvider provider,
                          String pushToken,
                          String apnsToken,
                          Boolean notificationPermissionGranted) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("디바이스 ID는 필수입니다.");
        }
        if (platform == null) {
            throw new IllegalArgumentException("플랫폼은 필수입니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("푸시 제공자는 필수입니다.");
        }
        if (provider != PushProvider.FCM) {
            throw new IllegalArgumentException("현재는 FCM 제공자만 지원합니다.");
        }
        if (pushToken == null || pushToken.isBlank()) {
            throw new IllegalArgumentException("푸시 토큰은 필수입니다.");
        }
        if (notificationPermissionGranted == null) {
            throw new IllegalArgumentException("알림 권한 허용 여부는 필수입니다.");
        }
        if (platform == PushPlatform.ANDROID && apnsToken != null && !apnsToken.isBlank()) {
            throw new IllegalArgumentException("안드로이드에서는 APNS 토큰을 사용할 수 없습니다.");
        }
    }
}
